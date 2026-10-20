package io.github.ozozorz.aimaid.experiment;

import com.google.gson.*;
import io.github.ozozorz.aimaid.entity.task.*;
import io.github.ozozorz.aimaid.llm.config.LlmConfig;
import io.github.ozozorz.aimaid.llm.service.LlmService;
import io.github.ozozorz.aimaid.llm.tool.MaidTools;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** One sequential session, owned and ticked exclusively by the server thread. */
public final class ExperimentRunner implements AutoCloseable {
    public static final int ACTION_LIMIT = 8, DEFAULT_SECONDS = 300;
    private final MinecraftServer server;
    private final ServerPlayer owner;
    private final ServerLevel originalLevel;
    private final Vec3 originalPosition;
    private final float originalYaw, originalPitch;
    private final List<ExperimentCases.Trial> schedule;
    private final LlmConfig config;
    private final int seconds;
    private final ExperimentLog log;
    private final Consumer<String> notify;
    private final List<JsonObject> results = new ArrayList<>();
    private final JsonArray transcript = new JsonArray();
    private ExperimentScene scene;
    private LlmService service;
    private int next, settleTicks;
    private long started;
    private Long goalSince;
    private String lastTask = "";
    private String lastError = "";
    private boolean done;

    public ExperimentRunner(MinecraftServer server, ServerPlayer owner, LlmConfig source,
                            List<ExperimentCases.Trial> schedule, long orderSeed, int seconds,
                            Path output, Consumer<String> notify) throws IOException {
        if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
        if (schedule.isEmpty() || seconds < 1 || seconds > 600) throw new IllegalArgumentException("Invalid schedule or time limit");
        if (schedule.stream().map(ExperimentCases.Trial::id).distinct().count() != schedule.size())
            throw new IllegalArgumentException("Duplicate trial ID");
        if (owner.isSpectator()) throw new IllegalArgumentException("请使用创造或生存模式，旁观模式不能成为任务主人。");
        var level = server.getLevel(ExperimentScene.DIMENSION);
        if (level == null) throw new IllegalArgumentException("实验维度未加载，请使用开发运行模块重新打开世界。");
        if (!level.players().isEmpty()) throw new IllegalArgumentException("实验维度内还有玩家，请先离开该维度。");
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING))
            throw new IllegalArgumentException("实验需要 mob_griefing=true，否则采收、砍树不能执行。");
        this.server = server; this.owner = owner; this.schedule = List.copyOf(schedule);
        this.seconds = seconds; this.notify = notify;
        originalLevel = owner.level(); originalPosition = owner.position();
        originalYaw = owner.getYRot(); originalPitch = owner.getXRot();
        config = new LlmConfig(source.getApiUrl(), source.getModel(), source.getApiKey(), source.systemPrompt(),
                source.tokenField(), source.timeoutSeconds(), source.maxTokens(), 0, 0, 1, source.maxToolRounds(), 64,
                new MaidGoalTask.Limits(ACTION_LIMIT, 3, 12000, 64));
        log = new ExperimentLog(output, manifest(orderSeed));
    }
    private JsonObject manifest(long seed) throws IOException {
        var m = new JsonObject();
        m.addProperty("schema_version", 1); m.addProperty("case_version", ExperimentCases.VERSION);
        m.addProperty("created_utc", Instant.now().toString()); m.addProperty("order_seed", seed);
        m.addProperty("expected_runs", schedule.size()); m.addProperty("wall_timeout_seconds", seconds);
        m.addProperty("action_limit", ACTION_LIMIT); m.addProperty("goal_failure_limit", 3);
        m.addProperty("goal_tick_limit", 12000); m.addProperty("goal_radius", 64);
        m.addProperty("model", config.getModel());
        m.addProperty("provider_host", java.net.URI.create(config.getApiUrl()).getHost());
        m.addProperty("system_prompt", config.systemPrompt()); m.addProperty("max_tokens", config.maxTokens());
        m.addProperty("token_field", config.tokenField()); m.addProperty("http_timeout_seconds", config.timeoutSeconds());
        m.addProperty("max_tool_rounds", config.maxToolRounds()); m.addProperty("history_turns", 0);
        m.addProperty("cooldown_seconds", 0); m.addProperty("max_concurrent", 1);
        m.addProperty("sampling", "Provider defaults; no temperature or sampling seed is sent. Actual request payloads are logged.");
        m.addProperty("scene_randomness", "Paired level/maid random seeds; no guarantee of identical loot or remote model sampling.");
        m.addProperty("scoring", "Final world goal and constraints, with required disturbance. N cases never enter feasible success rate. Refusal reasons require human review.");
        m.addProperty("timing", "Monotonic wall clock from chat submission; setup excluded, HTTP and execution included. elapsed_ms includes final dialogue; goal_since_ms is start of final continuously satisfied goal interval.");
        m.addProperty("java", System.getProperty("java.version"));
        var versions = new JsonObject();
        FabricLoader.getInstance().getAllMods().stream()
                .filter(c -> c.getMetadata().getId().startsWith("ai-maid") || Set.of("minecraft", "fabricloader", "aimaid-devrun").contains(c.getMetadata().getId()))
                .forEach(c -> versions.addProperty(c.getMetadata().getId(), c.getMetadata().getVersion().getFriendlyString()));
        m.add("versions", versions);
        try (var in = ExperimentRunner.class.getResourceAsStream("/experiment-build.json")) {
            if (in != null) m.add("build", JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            else m.addProperty("build", "unavailable");
        }
        m.add("cases", ExperimentCases.cards());
        var order = new JsonArray();
        for (var trial : schedule) {
            var row = new JsonObject(); row.addProperty("run_id", trial.id()); row.addProperty("seed", trial.seed()); order.add(row);
        }
        m.add("schedule", order);
        return m;
    }
    public boolean done() { return done; }
    public Path directory() { return log.directory(); }
    public List<JsonObject> results() { return results.stream().map(JsonObject::deepCopy).toList(); }
    public String status() {
        return (done ? "已结束" : "运行中") + " " + results.size() + "/" + schedule.size()
                + (scene == null ? "" : "，当前 " + scene.trial.id()) + "，数据：" + directory();
    }
    public void tick() {
        if (done) return;
        try {
            if (log.failure() != null) throw log.failure();
            if (server.getPlayerList().getPlayer(owner.getUUID()) != owner || !owner.isAlive()) { stop("owner_unavailable"); return; }
            if (scene == null) {
                if (next == schedule.size()) { close(); return; }
                var trial = schedule.get(next++);
                log.begin(trial.id()); transcript.asList().clear(); lastTask = ""; lastError = ""; goalSince = null; started = 0;
                scene = new ExperimentScene(server.getLevel(ExperimentScene.DIMENSION), owner, trial);
                var prepared = event("scene_ready"); prepared.add("initial", scene.initial); log.event(prepared);
                settleTicks = 20; // Allow chunk/entity/Brain initialization; excluded from task time.
                notify.accept("实验 " + next + "/" + schedule.size() + "：" + trial.id());
                return;
            }
            if (owner.level() != scene.level || scene.level.players().stream().anyMatch(p -> p != owner)) {
                stop("external_interference"); return;
            }
            if (service == null) {
                if (--settleTicks > 0) return;
                service = new LlmService(server, config, (p, id, kind, text, busy) -> {
                    var row = event("dialogue"); row.addProperty("kind", kind); row.addProperty("text", text); row.addProperty("busy", busy);
                    if (kind.equals("error")) lastError = text;
                    transcript.add(row.deepCopy()); log.event(row);
                }, scene.trial.policy(), log.trace());
                started = System.nanoTime();
                var begin = event("run_started"); begin.addProperty("instruction", ExperimentCases.instruction(scene.trial.card()));
                begin.addProperty("mode", scene.trial.mode()); log.event(begin);
                if (!service.chat(owner, scene.maid, ExperimentCases.instruction(scene.trial.card()))) finish("chat_rejected", false);
                return;
            }
            // Check the shared deadline before initiating another request or action.
            if (elapsed() >= seconds * 1000L) { finish("timeout", false); return; }
            service.tick();
            var disturbance = scene.perturb();
            if (disturbance != null) { disturbance.addProperty("elapsed_ms", elapsed()); log.event(disturbance); }
            var task = MaidTasks.current(scene.maid);
            if (task != null && task.snapshot(scene.maid).state() == MaidTask.State.CANCELLED) {
                stop("task_cancelled"); return;
            }
            var snapshot = taskJson();
            var comparable = snapshot.deepCopy();
            if (comparable.has("progress")) comparable.getAsJsonObject("progress").remove("active_ticks");
            if (!comparable.toString().equals(lastTask)) {
                lastTask = comparable.toString();
                var e = event("task_state"); e.add("task", snapshot); e.add("world", scene.snapshot()); log.event(e);
            }
            var measured = scene.snapshot();
            var score = ExperimentOracle.evaluate(scene.trial.card(), scene.initial, measured);
            if (objectiveSatisfied(score)) { if (goalSince == null) goalSince = elapsed(); }
            else goalSince = null;
            if (!service.busy(scene.maid.getUUID(), owner.getUUID())
                    && (task == null || task.snapshot(scene.maid).state().terminal()))
                finish(task == null ? (lastError.isEmpty() ? "no_task" : "model_error") : "task_terminal", false);
        } catch (Exception e) {
            notify.accept("实验已停止：" + e.getClass().getSimpleName() + "。请检查数据目录中的 session_error 事件。");
            var error = event("session_error"); error.addProperty("type", e.getClass().getSimpleName());
            var stack = new JsonArray(); Arrays.stream(e.getStackTrace()).limit(12).forEach(frame -> stack.add(frame.toString()));
            error.add("stack", stack); log.event(error);
            try { stop("harness_error"); } catch (Exception ignored) { close(); }
        }
    }
    private JsonObject event(String name) {
        var e = new JsonObject(); e.addProperty("event", name);
        if (started != 0) e.addProperty("elapsed_ms", elapsed());
        return e;
    }
    private long elapsed() { return started == 0 ? 0 : (System.nanoTime() - started) / 1_000_000; }
    private JsonObject taskJson() {
        var all = MaidTools.snapshot(scene.maid);
        return all.has("task") && all.get("task").isJsonObject() ? all.getAsJsonObject("task").deepCopy() : new JsonObject();
    }
    private boolean objectiveSatisfied(JsonObject score) {
        return score.get("goal_met").getAsBoolean() && score.get("constraints_ok").getAsBoolean()
                && (!scene.trial.card().group().equals("disturbed") || scene.disturbed);
    }
    private void finish(String reason, boolean interrupted) throws IOException {
        var trial = scene.trial;
        var task = MaidTasks.current(scene.maid);
        var state = task == null ? null : task.snapshot(scene.maid);
        var actions = task == null ? List.<MaidTask.ActionSnapshot>of() : task.actions(scene.maid);
        var world = scene.snapshot(); // Read before cancellation/cleanup changes task state.
        var result = ExperimentOracle.evaluate(trial.card(), scene.initial, world);
        boolean completionSignal = state != null && state.state() == MaidTask.State.SUCCEEDED;
        result.addProperty("run_id", trial.id()); result.addProperty("case_id", trial.card().id());
        result.addProperty("group", trial.card().group()); result.addProperty("repeat", trial.repeat());
        result.addProperty("mode", trial.mode()); result.addProperty("seed", trial.seed()); result.addProperty("feasible", trial.card().feasible());
        result.addProperty("success", trial.card().feasible() && !interrupted && objectiveSatisfied(result));
        result.add("correct_blocked", JsonNull.INSTANCE); // Never infer a correct explanation from a stopped task.
        result.addProperty("review_required", !trial.card().feasible() || task == null);
        result.addProperty("completion_signal", completionSignal);
        result.addProperty("false_completion_signal", completionSignal && !objectiveSatisfied(result));
        result.addProperty("end_reason", reason); result.addProperty("interrupted", interrupted);
        result.addProperty("task_reason", state == null ? "" : state.reason());
        result.addProperty("last_error", lastError); result.addProperty("disturbance_triggered", scene.disturbed);
        result.addProperty("elapsed_ms", elapsed());
        result.add("goal_since_ms", goalSince == null ? JsonNull.INSTANCE : new JsonPrimitive(goalSince));
        result.addProperty("actions_started", actions.stream().filter(a -> !Set.of("queued", "skipped", "pending").contains(a.state())).count());
        result.add("initial", scene.initial); result.add("final", world); result.add("task", taskJson());
        result.add("dialogue", transcript.deepCopy()); result.addProperty("expected_obstacle", trial.card().obstacle());
        if (service != null) { service.close(); service = null; }
        log.finish(result); results.add(result.deepCopy());
        notify.accept(trial.id() + "：" + (trial.card().feasible() ? result.get("success").getAsBoolean() ? "客观完成" : "未完成" : "受阻任务，待核对原因"));
        scene.close(); scene = null; started = 0;
    }
    public void stop(String reason) {
        if (done) return;
        try { if (scene != null) finish(reason, true); }
        catch (IOException e) { notify.accept("实验日志写入失败：" + e.getClass().getSimpleName()); }
        finally { close(); }
    }
    @Override public void close() {
        if (done) return;
        done = true;
        try {
            if (service != null) { service.close(); service = null; }
            if (scene != null) { scene.close(); scene = null; }
            var end = event("session_closed"); end.addProperty("completed_runs", results.size()); end.addProperty("expected_runs", schedule.size()); log.event(end);
            log.close();
        } catch (IOException e) { notify.accept("关闭实验日志失败：" + e.getClass().getSimpleName()); }
        finally {
            if (server.getPlayerList().getPlayer(owner.getUUID()) == owner && owner.isAlive()) {
                owner.teleportTo(originalLevel, originalPosition.x, originalPosition.y, originalPosition.z, Set.of(), originalYaw, originalPitch, true);
            }
            notify.accept(status());
        }
    }
}

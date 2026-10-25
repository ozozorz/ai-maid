package io.github.ozozorz.aimaid.experiment;

import io.github.ozozorz.aimaid.llm.config.LlmConfigLoader;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import com.google.gson.*;
import io.github.ozozorz.aimaid.llm.tool.MaidTools;
import io.github.ozozorz.aimaid.llm.service.ExecutionPolicy;
import io.github.ozozorz.aimaid.entity.task.MaidTasks;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidWorkNavigation;

/** Explicit opt-in real-provider pilot: twenty cases, one paired A/B repeat, unchanged scoring. */
public final class RealLlmPilotGameTest implements FabricClientGameTest {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("real-llm-pilot");

    @Override public void runTest(ClientGameTestContext context) {
        String configPath = System.getProperty("aimaid.experiment.realPilotConfig", "");
        String outputPath = System.getProperty("aimaid.experiment.realPilotOutput", "");
        String diagnosticPath = System.getProperty("aimaid.experiment.pilotDiagnosticOutput", "");
        if (!diagnosticPath.isBlank()) {
            if (!configPath.isBlank() || !outputPath.isBlank())
                throw new IllegalArgumentException("Diagnostic and real-provider modes must be separate");
            diagnoseNavigation(context, Path.of(diagnosticPath).toAbsolutePath());
            return;
        }
        if (configPath.isBlank() && outputPath.isBlank()) {
            LOGGER.info("Real LLM pilot skipped: explicit configuration and output paths are required.");
            return;
        }
        if (configPath.isBlank() || outputPath.isBlank())
            throw new IllegalArgumentException("Both realPilotConfig and realPilotOutput are required");
        var configFile = Path.of(configPath).toAbsolutePath();
        var output = Path.of(outputPath).toAbsolutePath();
        if (!Files.isRegularFile(configFile) || Files.exists(output.resolve("manifest.json")))
            throw new IllegalArgumentException("Pilot requires an existing configuration and unused output session");

        var active = new AtomicReference<ExperimentRunner>();
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            var runner = active.get();
            if (runner != null) runner.tick();
        });
        try {
            var config = LlmConfigLoader.load(configFile)
                    .orElseThrow(() -> new IllegalArgumentException("Real LLM is disabled"));
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer();
                var connection = world.getConnection();
                connection.waitForChunksRender();
                long seed = 20261020L;
                var schedule = ExperimentCases.schedule(ExperimentCases.ALL, 1, seed, null);
                if (schedule.size() != 40) throw new AssertionError("Pilot must contain exactly 40 trials");
                var runner = server.computeOnServer(s -> new ExperimentRunner(s, connection.getServerPlayer(), config,
                        schedule, seed, ExperimentRunner.DEFAULT_SECONDS, output, LOGGER::info));
                active.set(runner);
                try {
                    // The runner owns each 300-second deadline. This wait only bounds the whole batch.
                    server.waitFor(s -> runner.done(), 300000);
                    var rows = server.computeOnServer(s -> runner.results());
                    if (rows.size() != 40 || rows.stream().anyMatch(row -> row.get("interrupted").getAsBoolean()))
                        throw new AssertionError("Pilot interrupted: " + runner.status());
                    LOGGER.info("Real LLM pilot completed: 40 trials. Evidence: {}", output);
                } finally {
                    server.runOnServer(s -> { if (!runner.done()) runner.stop("pilot_driver_stopped"); });
                    active.set(null);
                }
            }
        } catch (Exception e) {
            throw new AssertionError("Real LLM pilot could not complete", e);
        } finally {
            active.set(null);
        }
    }

    /** Replays the observed legal action sequence without loading a provider configuration or calling an LLM. */
    private static void diagnoseNavigation(ClientGameTestContext context, Path output) {
        try {
            if (Files.exists(output)) throw new IllegalArgumentException("Diagnostic output already exists");
            var source = JsonParser.parseString(Files.readString(output.getParent().resolve("S5-1-A.json"))).getAsJsonObject();
            long seed = source.get("seed").getAsLong();
            var report = new JsonObject();
            report.addProperty("included_in_40_trials", false);
            report.addProperty("llm_calls", 0);
            report.addProperty("source_run", source.get("run_id").getAsString());
            report.addProperty("seed", seed);
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer();
                var connection = world.getConnection();
                connection.waitForChunksRender();
                for (boolean withMove : new boolean[] {true, false}) {
                    var scene = server.computeOnServer(s -> new ExperimentScene(s.getLevel(ExperimentScene.DIMENSION),
                            connection.getServerPlayer(), new ExperimentCases.Trial(ExperimentCases.get("S5"), 1, ExecutionPolicy.FIXED_PLAN, seed)));
                    try {
                        context.waitTicks(20);
                        var row = server.computeOnServer(s -> {
                            var observations = MaidTools.execute("observe_nearby", JsonParser.parseString("{\"radius\":10}").getAsJsonObject(), scene.maid, scene.owner, 32);
                            JsonObject tree = null;
                            for (var value : observations.getAsJsonArray("targets"))
                                if (value.getAsJsonObject().get("kind").getAsString().equals("tree")) tree = value.getAsJsonObject();
                            if (tree == null) throw new AssertionError("Diagnostic tree not observed");
                            var data = new JsonObject(); data.add("observed_tree", tree.deepCopy());
                            data.addProperty("stand_at_observation_range_3", MaidWorkNavigation.chooseStand(scene.maid, ExperimentScene.TREE, 3).map(Object::toString).orElse("none"));
                            data.addProperty("stand_at_move_range_2_5", MaidWorkNavigation.chooseStand(scene.maid, ExperimentScene.TREE, 2.5).map(Object::toString).orElse("none"));
                            var plan = new JsonObject(); plan.addProperty("request_id", "navigation-diagnostic");
                            var steps = new JsonArray();
                            if (withMove) {
                                var move = new JsonObject(); move.addProperty("action", "ai-maid:move_to"); move.add("target_id", tree.get("target_id")); steps.add(move);
                            }
                            var chop = new JsonObject(); chop.addProperty("action", "ai-maid-lumber:chop_tree"); chop.add("target_id", tree.get("target_id")); steps.add(chop);
                            plan.add("steps", steps); data.add("submitted_plan", plan.deepCopy());
                            data.add("acceptance", MaidTools.execute("submit_plan", plan, scene.maid, scene.owner, 32));
                            if (MaidTasks.current(scene.maid) == null) throw new AssertionError("Diagnostic plan rejected");
                            return data;
                        });
                        server.waitFor(s -> MaidTasks.current(scene.maid).snapshot(scene.maid).state().terminal(), 800);
                        server.runOnServer(s -> {
                            row.add("task", MaidTools.snapshot(scene.maid).get("task"));
                            row.add("final", scene.snapshot());
                        });
                        report.add(withMove ? "move_then_chop" : "direct_chop", row);
                    } finally {
                        server.runOnServer(s -> scene.close());
                    }
                }
            }
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report), java.nio.file.StandardOpenOption.CREATE_NEW);
            LOGGER.info("Navigation diagnostic saved; no LLM requests: {}", output);
        } catch (Exception e) {
            throw new AssertionError("Navigation diagnostic could not complete", e);
        }
    }
}

package io.github.ozozorz.aimaid.experiment;

import com.mojang.brigadier.arguments.*;
import io.github.ozozorz.aimaid.llm.config.LlmConfigLoader;
import io.github.ozozorz.aimaid.llm.service.ExecutionPolicy;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Development module only. Explicit commands start real, potentially billable model requests. */
public final class ExperimentAddon implements ModInitializer {
    private static final Map<MinecraftServer, ExperimentRunner> RUNNERS = new IdentityHashMap<>();
    @Override public void onInitialize() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            var runner = RUNNERS.get(server);
            if (runner != null) runner.tick();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            var runner = RUNNERS.remove(server); if (runner != null) runner.stop("server_stopped");
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, build, selection) -> dispatcher.register(
                Commands.literal("maidexperiment").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(c -> say(c.getSource(), "用法：list；run <场景> <A|B> [重复次数]；batch [重复次数] [种子]；status；stop。每轮最多8个动作、300秒。"))
                .then(Commands.literal("list").executes(c -> {
                    for (var card : ExperimentCases.ALL) say(c.getSource(), card.id() + " [" + card.group() + "] " + card.instruction());
                    return ExperimentCases.ALL.size();
                }))
                .then(Commands.literal("status").executes(c -> {
                    var runner = RUNNERS.get(c.getSource().getServer());
                    return say(c.getSource(), runner == null ? "尚未开始实验。" : runner.status());
                }))
                .then(Commands.literal("stop").executes(c -> {
                    var runner = RUNNERS.get(c.getSource().getServer());
                    if (runner != null) runner.stop("operator_stopped");
                    return say(c.getSource(), "实验已停止，已有结果保留。");
                }))
                .then(Commands.literal("run").then(Commands.argument("case", StringArgumentType.word())
                        .suggests((c, b) -> { ExperimentCases.ALL.forEach(card -> b.suggest(card.id())); return b.buildFuture(); })
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((c,b) -> b.suggest("A").suggest("B").buildFuture())
                                .executes(c -> single(c.getSource(), StringArgumentType.getString(c,"case"), StringArgumentType.getString(c,"mode"), 1))
                                .then(Commands.argument("repeats", IntegerArgumentType.integer(1,20))
                                        .executes(c -> single(c.getSource(), StringArgumentType.getString(c,"case"), StringArgumentType.getString(c,"mode"), IntegerArgumentType.getInteger(c,"repeats")))))))
                .then(Commands.literal("batch").executes(c -> start(c.getSource(), ExperimentCases.ALL, null, 5, 20261020L))
                        .then(Commands.argument("repeats", IntegerArgumentType.integer(1,20))
                                .executes(c -> start(c.getSource(), ExperimentCases.ALL, null, IntegerArgumentType.getInteger(c,"repeats"), 20261020L))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(c -> start(c.getSource(), ExperimentCases.ALL, null, IntegerArgumentType.getInteger(c,"repeats"), LongArgumentType.getLong(c,"seed"))))))));
    }
    private static int single(CommandSourceStack source, String id, String mode, int repeats) {
        try {
            var policy = switch (mode.toUpperCase(Locale.ROOT)) {
                case "A" -> ExecutionPolicy.FIXED_PLAN;
                case "B" -> ExecutionPolicy.STEPWISE_GOAL;
                default -> throw new IllegalArgumentException("模式只能为 A 或 B。");
            };
            return start(source, List.of(ExperimentCases.get(id)), policy, repeats, 20261020L);
        } catch (IllegalArgumentException e) { source.sendFailure(Component.literal(e.getMessage())); return 0; }
    }
    private static int start(CommandSourceStack source, List<ExperimentCases.Card> cards, ExecutionPolicy mode, int repeats, long seed) {
        var old = RUNNERS.get(source.getServer());
        if (old != null && !old.done()) { source.sendFailure(Component.literal("已有实验正在运行。")); return 0; }
        try {
            var owner = source.getPlayerOrException();
            var config = LlmConfigLoader.load(FabricLoader.getInstance().getConfigDir().resolve("ai-maid-llm.json"));
            if (config.isEmpty()) { source.sendFailure(Component.literal("请先启用并配置 ai-maid-llm.json。")); return 0; }
            String name = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0,8);
            var dir = FabricLoader.getInstance().getGameDir().resolve("maid-experiments").resolve(name);
            var runner = new ExperimentRunner(source.getServer(), owner, config.get(), ExperimentCases.schedule(cards,repeats,seed,mode),
                    seed, ExperimentRunner.DEFAULT_SECONDS, dir, message -> owner.sendSystemMessage(Component.literal(message)));
            RUNNERS.put(source.getServer(), runner);
            return say(source, "已开始实验，将调用配置中的模型。实验期间请保持在线，不操作场景。数据：" + dir);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage())); return 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("无法开始实验：" + e.getClass().getSimpleName())); return 0;
        }
    }
    private static int say(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false); return 1;
    }
}

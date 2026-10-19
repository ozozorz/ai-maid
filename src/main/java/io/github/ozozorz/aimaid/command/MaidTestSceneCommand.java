package io.github.ozozorz.aimaid.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;

import java.util.List;

/** 管理员手动验收入口；不通过 LLM 工具暴露，也不依赖工作 Addon 的实现类。 */
public final class MaidTestSceneCommand {
    private MaidTestSceneCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("test-scene")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(context -> {
                    var source = context.getSource();
                    var player = source.getPlayerOrException();
                    var level = player.level();
                    if (level.dimension() != Level.OVERWORLD) throw error("overworld");
                    if (!player.isAlive() || player.isSpectator() || player.isPassenger()) throw error("player");

                    // 先找空中位置并检查整个占用范围，再修改世界。
                    var floor = MaidTestScene.findFloor(level, player.blockPosition());
                    MaidTestScene.create(level, floor, player);
                    player.closeContainer();
                    player.connection.teleport(floor.getX() + 0.5, floor.getY() + 1,
                            floor.getZ() - 2.5, 0, 10);
                    player.resetFallDistance();
                    source.sendSuccess(() -> Component.translatable("command.ai-maid.test_scene.created",
                            floor.getX(), floor.getY(), floor.getZ()), false);
                    source.sendSuccess(() -> Component.translatable("command.ai-maid.test_scene.guide"), false);
                    var missing = List.of("ai-maid-llm", "ai-maid-lumber", "ai-maid-farming", "ai-maid-feeding", "ai-maid-hauling")
                            .stream().filter(id -> !FabricLoader.getInstance().isModLoaded(id)).toList();
                    if (!missing.isEmpty()) source.sendSuccess(() -> Component.translatable(
                            "command.ai-maid.test_scene.missing", String.join(", ", missing)), false);
                    return 1;
                });
    }

    static com.mojang.brigadier.exceptions.CommandSyntaxException error(String reason) {
        return new SimpleCommandExceptionType(Component.translatable("command.ai-maid.test_scene." + reason)).create();
    }
}

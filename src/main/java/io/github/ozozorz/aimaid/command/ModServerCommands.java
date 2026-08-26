package io.github.ozozorz.aimaid.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.github.ozozorz.aimaid.AIMaid;
import io.github.ozozorz.aimaid.command.maidtargetresolver.EntityArgumentMaidTargetResolver;
import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetResolver;
import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetSuggestions;
import io.github.ozozorz.aimaid.command.maidtargetresolver.NameMaidTargetResolver;
import io.github.ozozorz.aimaid.command.maidtargetresolver.NearestMaidTargetResolver;
import io.github.ozozorz.aimaid.command.maidtargetresolver.UuidMaidTargetResolver;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.resources.Identifier;

public class ModServerCommands {

    private ModServerCommands() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT
                .register((dispatcher, buildContext, selection) -> register(dispatcher, buildContext));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext) {

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("maid");

        // 1 默认目标
        //
        // /maid command follow
        root.then(createCommandNode(buildContext, NearestMaidTargetResolver.INSTANCE));

        // 2 老的 Vanilla EntityArgument
        //
        // /maid @e[...] command follow
        root.then(
                Commands.argument("maid", EntityArgument.entity())
                        .then(createCommandNode(buildContext,
                                new EntityArgumentMaidTargetResolver("maid"))));

        // 3 显示 nearest
        //
        // /maid nearest command follow
        root.then(
                Commands.literal("nearest")
                        .then(createCommandNode(buildContext,
                                NearestMaidTargetResolver.INSTANCE)));

        // 4 UUID
        //
        // /maid uuid <uuid> command follow
        root.then(
                Commands.literal("uuid")
                        .then(
                                Commands.argument("maid_uuid", UuidArgument.uuid())
                                        .suggests(MaidTargetSuggestions::suggestUuids)
                                        .then(createCommandNode(buildContext,
                                                new UuidMaidTargetResolver(
                                                        "maid_uuid")))));

        // 5 名字
        //
        // /maid name "Alice" command follow
        root.then(
                Commands.literal("name")
                        .then(
                                Commands.argument("maid_name",
                                        StringArgumentType.string())
                                        .suggests(MaidTargetSuggestions::suggestNames)
                                        .then(createCommandNode(buildContext,
                                                new NameMaidTargetResolver(
                                                        "maid_name")))));

        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createCommandNode(CommandBuildContext buildContext,
            MaidTargetResolver targetResolver) {

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("command");

        for (MaidCommand maidCommand : ModBuiltInRegistries.MAID_COMMAND) {

            Identifier id = maidCommand.getRegistryId();

            String literalName = id.getNamespace().equals(AIMaid.MOD_ID) ? id.getPath() : id.toString();

            LiteralArgumentBuilder<CommandSourceStack> commandNode = Commands.literal(literalName);

            maidCommand.buildServerCommand(commandNode, buildContext, targetResolver);

            root.then(commandNode);
        }

        return root;
    }

}

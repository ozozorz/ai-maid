package io.github.ozozorz.aimaid.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.ozozorz.aimaid.AIMaid;
import io.github.ozozorz.aimaid.command.maidtargetresolver.EntityArgumentMaidTargetResolver;
import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetResolver;
import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetSuggestions;
import io.github.ozozorz.aimaid.command.maidtargetresolver.NameMaidTargetResolver;
import io.github.ozozorz.aimaid.command.maidtargetresolver.NearestMaidTargetResolver;
import io.github.ozozorz.aimaid.command.maidtargetresolver.UuidMaidTargetResolver;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.menu.MaidMenuOpener;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class ModServerCommands {

	private static final SimpleCommandExceptionType ERROR_GUI_NOT_OWNER = new SimpleCommandExceptionType(Component.translatable("command.ad-maid.maid.not_owner"));

	private static final SimpleCommandExceptionType ERROR_GUI_UNAVAILABLE = new SimpleCommandExceptionType(Component.translatable("command.ai-maid.maid.gui_unavailable"));

    private ModServerCommands() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			register(dispatcher, buildContext);
		});
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("maid");

        // 1 默认目标
        //
        // /maid command follow
        root.then(createCommandNode(buildContext, NearestMaidTargetResolver.INSTANCE));
        root.then(createGuiNode(NearestMaidTargetResolver.INSTANCE));

        // 2 老的 Vanilla EntityArgument
        //
        // /maid @e[...] command follow
        root.then(Commands.argument("maid", EntityArgument.entity())
			.then(createCommandNode(buildContext, new EntityArgumentMaidTargetResolver("maid")))
			.then(createGuiNode(new EntityArgumentMaidTargetResolver("maid")))
		);

        // 3 显示 nearest
        //
        // /maid nearest command follow
        root.then(Commands.literal("nearest")
			.then(createCommandNode(buildContext, NearestMaidTargetResolver.INSTANCE))
			.then(createGuiNode(NearestMaidTargetResolver.INSTANCE))
		);

        // 4 UUID
        //
        // /maid uuid <uuid> command follow
        root.then(Commands.literal("uuid")
			.then(Commands.argument("maid_uuid", UuidArgument.uuid())
				.suggests(MaidTargetSuggestions::suggestUuids)
				.then(createCommandNode(buildContext, new UuidMaidTargetResolver("maid_uuid")))
				.then(createGuiNode(new UuidMaidTargetResolver("maid_uuid")))
			)
		);

        // 5 名字
        //
        // /maid name "Alice" command follow
        root.then(Commands.literal("name")
			.then(Commands.argument("maid_name", StringArgumentType.string())
				.suggests(MaidTargetSuggestions::suggestNames)
				.then(createCommandNode(buildContext, new NameMaidTargetResolver("maid_name")))
				.then(createGuiNode(new NameMaidTargetResolver("maid_name")))
			)
		);

        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createCommandNode(CommandBuildContext buildContext, MaidTargetResolver targetResolver) {

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

	private static LiteralArgumentBuilder<CommandSourceStack> createGuiNode(MaidTargetResolver targetResolver) {
		return Commands.literal("gui").executes(context -> {
			return openGui(context, targetResolver);
		});
	}

	private static int openGui(CommandContext<CommandSourceStack> context, MaidTargetResolver targetResolver) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		AiMaidEntity maid = targetResolver.resolve(context);

		// Target Resolver 负责：“你说的是哪只 Maid？”
		// GUI action 自己负责：“你有没有资格打开？”
		if (!maid.isTame() || !maid.isOwnedBy(player)) {
			throw ERROR_GUI_NOT_OWNER.create();
		}

		if (!MaidMenuOpener.open(player, maid)) {
			throw ERROR_GUI_UNAVAILABLE.create();
		}

		return 1;
	}

}

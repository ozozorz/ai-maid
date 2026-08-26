package io.github.ozozorz.aimaid.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetResolver;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class MaidCommandCommandApi {

    private static final SimpleCommandExceptionType ERROR_NOT_OWNER = new SimpleCommandExceptionType(
            Component.translatable("command.ai-maid.maid.not_owner"));

    private static final DynamicCommandExceptionType ERROR_COMMAND_UNAVAILIABLE = new DynamicCommandExceptionType(
            commandName -> Component.translatable("command.ai-maid.maid.command_unavailable", commandName));

    private MaidCommandCommandApi() {
    }

    public static AiMaidEntity resolveSelectableTarget(CommandContext<CommandSourceStack> context,
            MaidTargetResolver targetResolver, MaidCommand maidCommand) throws CommandSyntaxException {

        ServerPlayer player = context.getSource().getPlayerOrException();

        AiMaidEntity maid = targetResolver.resolve(context);

        if (!maid.isTame() || !maid.isOwnedBy(player)) {
            throw ERROR_NOT_OWNER.create();
        }

        if (!maidCommand.canPlayerSelect(maid, player)) {
            throw ERROR_COMMAND_UNAVAILIABLE.create(maidCommand.getDisplayName());
        }

        return maid;
    }

    public static int finishSelection(CommandContext<CommandSourceStack> context, AiMaidEntity maid,
            MaidCommand maidCommand) {

        maid.setMaidCommand(maidCommand);

        context.getSource().sendSuccess(() -> Component.translatable("command.ai-maid.maid,command_changed",
                maid.getDisplayName(), maidCommand.getDisplayName()), false);

        return 1;
    }

    public static int executeSelection(CommandContext<CommandSourceStack> context, MaidTargetResolver targetResolver,
            MaidCommand maidCommand)
            throws CommandSyntaxException {

        AiMaidEntity maid = resolveSelectableTarget(context, targetResolver, maidCommand);

        return finishSelection(context, maid, maidCommand);
    }

}

package io.github.ozozorz.aimaid.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetResolver;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class MaidCommandCommandApi {

    private MaidCommandCommandApi() {
    }

    public static int executeSelection(CommandContext<CommandSourceStack> context, MaidTargetResolver targetResolver,
            MaidCommand maidCommand)
            throws CommandSyntaxException {

        CommandSourceStack source = context.getSource();

        ServerPlayer player = source.getPlayerOrException();

        AiMaidEntity maid = targetResolver.resolve(context);

        if (!maid.isTame() || !maid.isOwnedBy(player)) {
            source.sendFailure(Component.translatable("command.ai-maid.maid.not_owner"));
            return 0;
        }

        if (!maidCommand.canPlayerSelect(maid, player)) {
            source.sendFailure(
                    Component.translatable("command.ai-maid.maid.command_unavailable", maidCommand.getDisplayName()));
            return 0;
        }

        maid.setMaidCommand(maidCommand);

        source.sendSuccess(() -> Component.translatable("command.ai-maid.maid.command_changed", maid.getDisplayName(),
                maidCommand.getDisplayName()), false);

        return 1;
    }

}

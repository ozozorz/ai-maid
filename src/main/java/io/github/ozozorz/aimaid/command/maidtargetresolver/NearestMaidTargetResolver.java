package io.github.ozozorz.aimaid.command.maidtargetresolver;

import java.util.Comparator;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class NearestMaidTargetResolver implements MaidTargetResolver {

    public static final NearestMaidTargetResolver INSTANCE = new NearestMaidTargetResolver();

    private static final SimpleCommandExceptionType ERROR_NO_MAID = new SimpleCommandExceptionType(
            Component.translatable("command.ai-maid.maid.no_owned_maid_nearby"));

    private NearestMaidTargetResolver() {

    }

    @Override
    public AiMaidEntity resolve(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {

        ServerPlayer player = context.getSource().getPlayerOrException();

        return MaidTargetQueries
                .findOwnedMaidsNearby(player)
                .stream()
                .min(Comparator.comparingDouble(maid -> maid.distanceToSqr(player)))
                .orElseThrow(ERROR_NO_MAID::create);

    }

}

package io.github.ozozorz.aimaid.command.maidtargetresolver;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class NameMaidTargetResolver implements MaidTargetResolver {

    private static final double SEARCH_RADIUS = 128.0D;

    private static final SimpleCommandExceptionType ERROR_NOT_FOUND = new SimpleCommandExceptionType(
            Component.translatable("command.ai-maid.maid.name_not_found"));

    private static final SimpleCommandExceptionType ERROR_AMBIGUOUS = new SimpleCommandExceptionType(
            Component.translatable("command.ai-maid.maid.name_ambiguous"));

    private final String argumentName;

    public NameMaidTargetResolver(String argumentName) {
        this.argumentName = argumentName;
    }

    @Override
    public AiMaidEntity resolve(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        String name = StringArgumentType.getString(context, this.argumentName);

        List<AiMaidEntity> matches = player.level().getEntitiesOfClass(AiMaidEntity.class,
                player.getBoundingBox().inflate(SEARCH_RADIUS),
                maid -> maid.isAlive() && maid.isTame() && maid.isOwnedBy(player) && maid.hasCustomName()
                        && maid.getCustomName().getString().equals(name));

        if (matches.isEmpty()) {
            throw ERROR_NOT_FOUND.create();
        }

        if (matches.size() > 1) {
            throw ERROR_AMBIGUOUS.create();
        }

        return matches.getFirst();
    }

}

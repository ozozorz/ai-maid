package io.github.ozozorz.aimaid.command.maidtargetresolver;

import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class UuidMaidTargetResolver implements MaidTargetResolver {

    private static final SimpleCommandExceptionType ERROR_NOT_FOUND = new SimpleCommandExceptionType(
            Component.translatable("command.ai-maid.maid.uuid_not_found"));

    private final String argumentName;

    public UuidMaidTargetResolver(String argumentName) {
        this.argumentName = argumentName;
    }

    @Override
    public AiMaidEntity resolve(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID uuid = UuidArgument.getUuid(context, this.argumentName);
        Entity entity = context.getSource().getLevel().getEntityInAnyDimension(uuid);
        if (!(entity instanceof AiMaidEntity maid)) {
            throw ERROR_NOT_FOUND.create();
        }
        return maid;
    }

}

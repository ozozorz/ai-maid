package io.github.ozozorz.aimaid.command.maidtargetresolver;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class EntityArgumentMaidTargetResolver implements MaidTargetResolver {

    private static final DynamicCommandExceptionType ERROR_NOT_MAID = new DynamicCommandExceptionType(
            value -> Component.translatable("command.ai-maid.maid.not_maid", value));

    private final String argumentName;

    public EntityArgumentMaidTargetResolver(String argumentName) {
        this.argumentName = argumentName;
    }

    @Override
    public AiMaidEntity resolve(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(context, this.argumentName);
        if (!(entity instanceof AiMaidEntity maid)) {
            throw ERROR_NOT_MAID.create(entity.getDisplayName());
        }
        return maid;
    }

}

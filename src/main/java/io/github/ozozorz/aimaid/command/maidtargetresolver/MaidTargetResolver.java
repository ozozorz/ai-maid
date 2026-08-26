package io.github.ozozorz.aimaid.command.maidtargetresolver;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.commands.CommandSourceStack;

// 根据当前这条命令，到底是哪一只 Maid？
@FunctionalInterface
public interface MaidTargetResolver {

    AiMaidEntity resolve(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;

}

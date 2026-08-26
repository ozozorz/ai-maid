package io.github.ozozorz.aimaid.test;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.github.ozozorz.aimaid.command.MaidCommandCommandApi;
import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetResolver;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.schedule.Activity;

public class TestPatrolCommand implements MaidCommand {

    @Override
    public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of(ActivityData.create(TestAddonActivities.PATROL, 10, ImmutableList.of(new DoNothing(40, 80))));
    }

    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return List.of(TestAddonActivities.PATROL, Activity.IDLE);
    }

    @Override
    public int getMenuOrder() {
        return 400;
    }

    @Override
    public void buildServerCommand(LiteralArgumentBuilder<CommandSourceStack> node, CommandBuildContext buildContext,
            MaidTargetResolver targetResolver) {
        node.then(
                Commands.argument("radius", IntegerArgumentType.integer(2, 32))
                        .executes(context -> {
                            int radius = IntegerArgumentType.getInteger(context, "radius");
                            // 这里以后：
                            // 给“这一只 Maid”保存 patrol radius
                            //
                            // 然后再切换到 PATROL command。

                            return MaidCommandCommandApi.executeSelection(context, targetResolver, this);
                        }));

    }

}

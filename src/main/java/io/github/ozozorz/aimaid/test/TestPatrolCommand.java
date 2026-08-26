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

        // 不带参数：
        //
        // /maid command testaddon:patrol
        //
        // 使用这只 Maid 已经保存的 radius；
        // 如果从来没设置过，就使用默认 4。
        node.executes(context -> MaidCommandCommandApi.executeSelection(context, targetResolver, this));

        // 带 radius：
        //
        // /maid command testaddon:patrol 4
        node.then(
                Commands.argument("radius", IntegerArgumentType.integer(2, 32))
                        .executes(context -> {

                            int radius = IntegerArgumentType.getInteger(context, "radius");

                            AiMaidEntity maid = MaidCommandCommandApi.resolveSelectableTarget(context, targetResolver,
                                    this);

                            TestAddonMaidData.setPatrolRadius(maid, radius);

                            return MaidCommandCommandApi.finishSelection(context, maid, this);
                        }));

    }

}

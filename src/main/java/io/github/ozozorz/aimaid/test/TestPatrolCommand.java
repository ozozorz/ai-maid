package io.github.ozozorz.aimaid.test;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;

import io.github.ozozorz.aimaid.command.MaidCommandCommandApi;
import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetResolver;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.RandomLookAround;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;

public class TestPatrolCommand implements MaidCommand {

    @Override
    public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of(createPatrolActivity());
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

    @Override
    public void onSelected(AiMaidEntity maid) {
        TestAddonMaidData.setPatrolCenter(maid);
    }

    private ActivityData<AiMaidEntity> createPatrolActivity() {
        return ActivityData.create(
                TestAddonActivities.PATROL,
                ActivityData.createPriorityPairs(
                        10,
                        ImmutableList.of(
                                TestChoosePatrolTarget.create(),
                                TestWalkToPatrolTarget.create(0.7F),
                                createPatrolAmbientBehaviors())),
                Set.of(),
                Set.of(
                        TestAddonMemoryModuleTypes.PATROL_TARGET,
                        MemoryModuleType.WALK_TARGET,
                        MemoryModuleType.LOOK_TARGET));
    }

    private RunOne<AiMaidEntity> createPatrolAmbientBehaviors() {

        return new RunOne<>(
                ImmutableList.of(
                        Pair.of(new RandomLookAround(UniformInt.of(40, 80), 45.0F, -15.0F, 20.0F), 3),
                        Pair.of(new DoNothing(20, 40), 2)));

    }

}

package io.github.ozozorz.aimaid.entity.maidcommand;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.SetOwnerLookTarget;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.EraseMemoryIf;
import net.minecraft.world.entity.ai.behavior.RandomLookAround;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;

public class StayMaidCommand implements MaidCommand {

    @Override
    public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of(createStayActivity());
    }

    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return List.of(ModActivities.STAY);
    }

    private static ActivityData<AiMaidEntity> createStayActivity() {
        return ActivityData.create(
                ModActivities.STAY,

                ActivityData.createPriorityPairs(
                        0,
                        ImmutableList.of(
                                createStopWalkingBehavior(),
                                createStayAmbientBehaviors())),

                Set.of(),

                Set.of(MemoryModuleType.LOOK_TARGET));
    }

    private static BehaviorControl<AiMaidEntity> createStopWalkingBehavior() {
        return EraseMemoryIf.create(maid -> true, MemoryModuleType.WALK_TARGET);
    }

    private static RunOne<AiMaidEntity> createStayAmbientBehaviors() {
        return new RunOne<>(ImmutableList.of(
                Pair.of(SetOwnerLookTarget.create(8.0), 4),
                Pair.of(new RandomLookAround(UniformInt.of(40, 80), 45.0F, -15.0F,
                        20.0F), 3),
                Pair.of(new DoNothing(30, 60), 4)));
    }

}

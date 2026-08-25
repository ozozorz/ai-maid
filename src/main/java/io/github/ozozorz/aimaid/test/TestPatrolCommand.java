package io.github.ozozorz.aimaid.test;

import java.util.List;

import com.google.common.collect.ImmutableList;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
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

}

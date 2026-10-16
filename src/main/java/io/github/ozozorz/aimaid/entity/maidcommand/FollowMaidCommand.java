package io.github.ozozorz.aimaid.entity.maidcommand;

import net.minecraft.network.chat.Component;
import io.github.ozozorz.aimaid.entity.ai.behavior.FollowOwner;

import java.util.List;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.AiMaidAi;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.schedule.Activity;

public class FollowMaidCommand implements MaidCommand {

    @Override
    public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of(AiMaidAi.createFollowOwnerActivity());
    }

    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return maid.isTame() ? List.of(ModActivities.FOLLOW_OWNER, ModActivities.WAIT_FOR_OWNER, Activity.IDLE)
                : List.of(Activity.IDLE);
    }

    @Override public Component getStatus(AiMaidEntity maid) {
        return maid.isTame() ? FollowOwner.status(maid)
                : Component.empty();
    }

    @Override
    public int getMenuOrder() {
        return 200;
    }

}

package io.github.ozozorz.aimaid.entity.maidcommand;

import java.util.List;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

public class FreeMaidCommand implements MaidCommand {

    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return List.of(ModActivities.PICK_UP_ITEM, Activity.IDLE);
    }

    @Override
    public boolean allowsItemPickup(AiMaidEntity maid, ServerLevel level, ItemStack itemStack) {
        return true;
    }

}

package io.github.ozozorz.aimaid.entity.maidcommand;

import java.util.List;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.world.entity.schedule.Activity;

public class FreeMaidCommand implements MaidCommand {

    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return List.of(Activity.IDLE);
    }

}

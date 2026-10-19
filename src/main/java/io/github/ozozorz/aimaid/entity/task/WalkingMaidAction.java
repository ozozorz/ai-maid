package io.github.ozozorz.aimaid.entity.task;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidWorkNavigation;
import net.minecraft.core.BlockPos;

/** Shared physical navigation, with per-action progress and no teleporting. */
public abstract class WalkingMaidAction implements MaidAction {
    protected MaidTask.State state = MaidTask.State.RUNNING;
    protected String reason = "";
    protected final MaidWorkNavigation.Progress navigation = new MaidWorkNavigation.Progress();
    protected BlockPos target, feet;
    protected int interaction;
    protected void fail(String why) { state = MaidTask.State.FAILED; reason = why; }
    protected boolean approach(AiMaidEntity maid, BlockPos pos, double reach, long now, String failure) {
        if (MaidWorkNavigation.canReach(maid, pos, reach)) {
            MaidWorkNavigation.haltMovement(maid); MaidWorkNavigation.lookAt(maid, pos); navigation.reset(maid, now); return true;
        }
        if (target == null || !target.equals(pos)) { stop(maid); target = pos; }
        if (feet == null || !MaidWorkNavigation.canStand(maid, feet)
                || maid.getNavigation().isDone() && feet.distToCenterSqr(maid.position()) < 2) {
            var stand = MaidWorkNavigation.chooseStand(maid, pos, reach);
            if (stand.isEmpty()) { fail(failure); return false; }
            feet = stand.get();
        }
        if (navigation.stalled(maid, now, 100)) { fail(failure); return false; }
        interaction = 0; MaidWorkNavigation.walk(maid, feet, pos); return false;
    }
    @Override public void stop(AiMaidEntity maid) {
        MaidWorkNavigation.stop(maid); target = feet = null; interaction = 0;
        navigation.reset(maid, maid.level().getGameTime());
    }
}

package io.github.ozozorz.aimaid.entity.task;

import java.util.Map;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidWorkNavigation;

final class ReturnToOwnerAction extends WalkingMaidAction {
    @Override public Result result(AiMaidEntity maid) { return new Result(state, reason, Map.of()); }
    @Override public void tick(AiMaidEntity maid, long now) {
        MaidServerAccess.requireServerThread(maid);
        if (state.terminal()) return;
        var owner = maid.getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != maid.level()) { fail("owner_unavailable"); return; }
        if (MaidWorkNavigation.canReach(maid, owner, 3.0)) { state = MaidTask.State.SUCCEEDED; return; }
        // Use entity reach/stands, not the owner's feet block (which can be visible through a low wall).
        if (target == null || !target.equals(owner.blockPosition())) { stop(maid); target = owner.blockPosition(); }
        if (feet == null || !MaidWorkNavigation.canStand(maid, feet)
                || maid.getNavigation().isDone() && feet.distToCenterSqr(maid.position()) < 2) {
            var stand = MaidWorkNavigation.chooseStand(maid, owner, 3.0);
            if (stand.isEmpty()) { fail("owner_unreachable"); return; }
            feet = stand.get();
        }
        if (navigation.stalled(maid, now, 100)) { fail("owner_unreachable"); return; }
        MaidWorkNavigation.walk(maid, feet); MaidWorkNavigation.lookAt(maid, owner);
    }
}

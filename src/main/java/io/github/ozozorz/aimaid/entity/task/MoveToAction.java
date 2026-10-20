package io.github.ozozorz.aimaid.entity.task;

import java.util.Map;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;

final class MoveToAction extends WalkingMaidAction {
    private final MaidObservations.Target destination;
    MoveToAction(MaidObservations.Target destination) { this.destination = destination; }
    @Override public Result result(AiMaidEntity maid) { return new Result(state, reason, Map.of()); }
    @Override public void tick(AiMaidEntity maid, long now) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (state.terminal()) return;
        var pos = destination.position().pos();
        if (level.dimension() != destination.position().dimension() || !level.hasChunkAt(pos)) { fail("target_unavailable"); return; }
        if (destination.entity() != null && level.getBlockEntity(pos) != destination.entity()) { fail("chest_changed"); return; }
        if (approach(maid, pos, 2.5, now, "unreachable")) state = MaidTask.State.SUCCEEDED;
    }
}

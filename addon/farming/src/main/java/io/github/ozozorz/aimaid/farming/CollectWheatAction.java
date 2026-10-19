package io.github.ozozorz.aimaid.farming;

import java.util.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidWorkNavigation;
import io.github.ozozorz.aimaid.entity.task.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.gamerules.GameRules;

/** Ensure inventory holds a count of wheat; harvest/replant only the shortfall. */
public final class CollectWheatAction extends WalkingMaidAction {
    public static final int SEARCH_RADIUS = 16;
    private final BlockPos center;
    private final int requested;
    private final Set<BlockPos> unreachable = new HashSet<>();
    private int harvested;
    public CollectWheatAction(AiMaidEntity maid, int count) { center = maid.blockPosition(); requested = count; }
    public static int wheat(AiMaidEntity maid) { return maid.getInventory().countMatching(s -> s.is(Items.WHEAT)); }
    @Override public Result result(AiMaidEntity maid) {
        return new Result(state, reason, Map.of("requested", requested, "available", wheat(maid), "harvested", harvested,
                "remaining", Math.max(0, requested - wheat(maid))));
    }
    @Override public void tick(AiMaidEntity maid, long now) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (state.terminal()) return;
        if (wheat(maid) >= requested) { state = MaidTask.State.SUCCEEDED; return; }
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) { fail("disabled"); return; }
        if (target != null && !isMatureWheat(level, target)) stop(maid);
        if (target == null) {
            var candidates = matureWheat(level, center);
            candidates.removeAll(unreachable);
            candidates.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(maid.position())));
            if (candidates.isEmpty()) { fail(unreachable.isEmpty() ? "insufficient_wheat" : "crop_unreachable"); return; }
            var batch = candidates.stream().limit(16).toList();
            var chosen = MaidWorkNavigation.chooseWorkPosition(maid, batch, 3.0);
            if (chosen.isEmpty()) { unreachable.addAll(batch); return; }
            target = chosen.get().target(); feet = chosen.get().feet(); navigation.reset(maid, now);
        }
        if (!MaidWorkNavigation.canReach(maid, target, 3.0)) {
            if (navigation.stalled(maid, now, 100)) { unreachable.add(target); stop(maid); return; }
            if (feet == null || !MaidWorkNavigation.canStand(maid, feet)) {
                var stand = MaidWorkNavigation.chooseStand(maid, target, 3.0);
                if (stand.isEmpty()) { unreachable.add(target); stop(maid); return; }
                feet = stand.get();
            }
            interaction = 0; MaidWorkNavigation.walk(maid, feet, target); return;
        }
        MaidWorkNavigation.haltMovement(maid); MaidWorkNavigation.lookAt(maid, target);
        if (interaction++ == 0) maid.swing(InteractionHand.MAIN_HAND);
        if (interaction < 6) return;
        if (!isMatureWheat(level, target)) { stop(maid); return; }
        int before = wheat(maid);
        String result = FarmingActions.cultivate(level, maid, target);
        harvested += Math.max(0, wheat(maid) - before); stop(maid);
        if (!result.equals("working")) fail(result.equals("full") ? "inventory_full" : result);
    }
    public static List<BlockPos> matureWheat(ServerLevel level, BlockPos center) {
        var result = new ArrayList<BlockPos>();
        for (var pos : BlockPos.betweenClosed(center.offset(-SEARCH_RADIUS, -2, -SEARCH_RADIUS), center.offset(SEARCH_RADIUS, 2, SEARCH_RADIUS)))
            if (isMatureWheat(level, pos)) result.add(pos.immutable());
        return result;
    }
    private static boolean isMatureWheat(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getBlockState(pos.below()).is(Blocks.FARMLAND) && level.getBlockState(pos).is(Blocks.WHEAT)
                && ((CropBlock) Blocks.WHEAT).isMaxAge(level.getBlockState(pos));
    }
}

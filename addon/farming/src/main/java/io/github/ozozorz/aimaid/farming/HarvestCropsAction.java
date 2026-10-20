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

/** Ensure inventory holds a count of the requested crop; harvest/replant only the shortfall. */
public class HarvestCropsAction extends WalkingMaidAction {
    public static final int SEARCH_RADIUS = 16;
    private final BlockPos center;
    private final int requested, radius;
    private final net.minecraft.world.item.Item item;
    private final CropBlock crop;
    private final Set<BlockPos> unreachable = new HashSet<>();
    private int harvested;
    public HarvestCropsAction(AiMaidEntity maid, int count, net.minecraft.world.item.Item item, BlockPos center, int radius) {
        this.center = center.immutable(); requested = count; this.item = item; this.radius = radius;
        crop = CROPS.get(item);
        if (crop == null) throw new IllegalArgumentException("unsupported_crop");
    }
    public static final Map<net.minecraft.world.item.Item, CropBlock> CROPS = Map.of(
            Items.WHEAT, (CropBlock) Blocks.WHEAT, Items.CARROT, (CropBlock) Blocks.CARROTS,
            Items.POTATO, (CropBlock) Blocks.POTATOES, Items.BEETROOT, (CropBlock) Blocks.BEETROOTS);
    private int available(AiMaidEntity maid) { return maid.getInventory().countMatching(s -> s.is(item)); }
    @Override public Result result(AiMaidEntity maid) {
        return new Result(state, reason, Map.of("requested", requested, "available", available(maid), "harvested", harvested,
                "remaining", Math.max(0, requested - available(maid))));
    }
    @Override public void tick(AiMaidEntity maid, long now) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (state.terminal()) return;
        if (available(maid) >= requested) { state = MaidTask.State.SUCCEEDED; return; }
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) { fail("disabled"); return; }
        if (target != null && !isMature(level, target, crop)) stop(maid);
        if (target == null) {
            var candidates = matureCrops(level, center, radius, crop);
            candidates.removeAll(unreachable);
            candidates.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(maid.position())));
            if (candidates.isEmpty()) { fail(unreachable.isEmpty() ? (item == Items.WHEAT ? "insufficient_wheat" : "insufficient_crops") : "crop_unreachable"); return; }
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
        if (!isMature(level, target, crop)) { stop(maid); return; }
        int before = available(maid);
        String result = FarmingActions.cultivate(level, maid, target);
        harvested += Math.max(0, available(maid) - before); stop(maid);
        if (!result.equals("working")) fail(result.equals("full") ? "inventory_full" : result);
    }
    public static List<BlockPos> matureCrops(ServerLevel level, BlockPos center, int radius, CropBlock crop) {
        var result = new ArrayList<BlockPos>();
        for (var pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius)))
            if (isMature(level, pos, crop)) result.add(pos.immutable());
        return result;
    }
    private static boolean isMature(ServerLevel level, BlockPos pos, CropBlock crop) {
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getBlockState(pos.below()).is(Blocks.FARMLAND) && level.getBlockState(pos).is(crop)
                && crop.isMaxAge(level.getBlockState(pos));
    }
}

package io.github.ozozorz.aimaid.lumber.lumber;

import java.util.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import io.github.ozozorz.aimaid.entity.ai.behavior.*;
import io.github.ozozorz.aimaid.entity.task.*;
import io.github.ozozorz.aimaid.lumber.animation.LumberAnimationIds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Specified observed tree, incremental breaking, and physical collection of this action's own drops. */
public final class ChopTreeAction extends WalkingMaidAction {
    private final LumberJob job;
    private final List<ItemEntity> drops = new ArrayList<>();
    private float breakingProgress;
    private ItemStack toolSnapshot = ItemStack.EMPTY;
    private int collected, missingDrops, animationToken, settlingTicks;
    public ChopTreeAction(AiMaidEntity maid, MaidObservations.Target tree) {
        if (tree.blocks().isEmpty()) throw new IllegalArgumentException("empty_tree");
        job = new LumberJob(tree.blocks(), maid.level().getGameTime());
    }
    @Override public Result result(AiMaidEntity maid) {
        return new Result(state, reason, Map.of("broken", job.broken, "skipped", job.skipped, "collected", collected,
                "uncollected_entities", drops.size(), "missing_drop_entities", missingDrops, "remaining_blocks", job.remaining().size()));
    }
    @Override public void tick(AiMaidEntity maid, long now) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (state.terminal()) return;
        if (!drops.isEmpty()) { collectDrop(maid, now); return; }
        if (job.remaining().isEmpty()) {
            if (job.skipped > 0) fail("tree_partly_unreachable");
            else if (missingDrops > 0) fail("drops_missing");
            else state = MaidTask.State.SUCCEEDED;
            return;
        }
        var tool = maid.getMainHandItem();
        if (!tool.is(ItemTags.AXES)) { fail("missing_axe"); return; }
        if (target != null && level.getBlockState(target) != job.remaining().get(target)) {
            job.complete(target, false); stop(maid);
        }
        if (target == null) {
            var chosen = LumberWorkPlanner.choose(level, maid, job);
            if (chosen.isEmpty()) return;
            target = chosen.get().log(); feet = chosen.get().feet(); navigation.reset(maid, now);
        }
        if (!MaidBlockBreaking.canReach(level, maid, target)) {
            if (!LumberWorkPlanner.canStand(level, maid, feet) || navigation.stalled(maid, now, 100)) {
                job.complete(target, false); stop(maid); return;
            }
            MaidWorkNavigation.walk(maid, feet, target); return;
        }
        MaidWorkNavigation.haltMovement(maid); MaidWorkNavigation.lookAt(maid, target);
        var expected = job.remaining().get(target);
        if (!MaidBlockBreaking.canBreak(level, maid, target, expected, tool)) { fail("disabled"); return; }
        if (!maid.getInventory().canAddItem(new ItemStack(expected.getBlock().asItem()))) { fail("inventory_full"); return; }
        if (!ItemStack.isSameItemSameComponents(tool, toolSnapshot)) {
            breakingProgress = 0; toolSnapshot = tool.copy(); level.destroyBlockProgress(maid.getId(), target, -1);
        }
        if (interaction++ == 0) animationToken = maid.playAnimationAction(LumberAnimationIds.LUMBER, 1200);
        if (interaction % 6 == 0) maid.swing(InteractionHand.MAIN_HAND);
        breakingProgress += MaidBlockBreaking.progressPerTick(level, maid, target, expected, tool);
        if (breakingProgress < 1) { level.destroyBlockProgress(maid.getId(), target, Math.min(9, (int) (breakingProgress * 10))); return; }
        var bounds = new AABB(target).inflate(2);
        var before = new HashSet<>(level.getEntitiesOfClass(ItemEntity.class, bounds));
        boolean broken = MaidBlockBreaking.destroy(level, maid, target, expected, tool);
        if (broken) {
            for (var drop : level.getEntitiesOfClass(ItemEntity.class, bounds)) if (!before.contains(drop)) drops.add(drop);
        }
        job.complete(target, broken); stop(maid);
    }
    private void collectDrop(AiMaidEntity maid, long now) {
        var drop = drops.getFirst();
        if (!drop.isAlive()) { missingDrops++; drops.removeFirst(); stop(maid); return; }
        // Item entities need time to fall from a cut log. Block stand selection also reserves
        // 0.75 blocks for path arrival, so use the same physical reach as container interaction.
        if (!drop.onGround() && ++settlingTicks <= 40) return;
        if (!approach(maid, drop.blockPosition(), 2.5, now, "drops_unreachable")) return;
        if (drop.hasPickUpDelay()) return;
        int before = drop.getItem().getCount();
        var remainder = maid.getInventory().addItem(drop.getItem().copy());
        int moved = before - remainder.getCount(); collected += moved;
        if (remainder.isEmpty()) { drop.discard(); drops.removeFirst(); settlingTicks = 0; stop(maid); }
        else { drop.setItem(remainder); fail("inventory_full"); }
    }
    @Override public void stop(AiMaidEntity maid) {
        if (maid.level() instanceof ServerLevel level && target != null) level.destroyBlockProgress(maid.getId(), target, -1);
        maid.stopAnimationAction(LumberAnimationIds.LUMBER, animationToken);
        breakingProgress = 0; toolSnapshot = ItemStack.EMPTY; super.stop(maid);
    }
}

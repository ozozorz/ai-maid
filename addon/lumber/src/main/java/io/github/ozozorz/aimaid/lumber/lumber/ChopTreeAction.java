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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Specified observed tree, incremental breaking, and physical collection of this action's own drops. */
public final class ChopTreeAction extends WalkingMaidAction {
    private final LumberJob job;
    private final List<ItemEntity> drops = new ArrayList<>();
    private final List<ItemStack> pendingDrops = new ArrayList<>();
    private float breakingProgress;
    private ItemStack toolSnapshot = ItemStack.EMPTY;
    private int collected, missingDrops, animationToken, settlingTicks;
    public ChopTreeAction(AiMaidEntity maid, MaidObservations.Target tree) {
        if (tree.blocks().isEmpty()) throw new IllegalArgumentException("empty_tree");
        job = new LumberJob(tree.blocks(), maid.level().getGameTime());
    }
    @Override public Result result(AiMaidEntity maid) {
        return new Result(state, reason, Map.of("broken", job.broken, "skipped", job.skipped, "collected", collected,
                "uncollected_entities", drops.size(), "missing_drop_entities", missingDrops,
                "pending_drop_items", pendingDrops.stream().mapToInt(ItemStack::getCount).sum(), "remaining_blocks", job.remaining().size()));
    }
    @Override public void tick(AiMaidEntity maid, long now) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (state.terminal()) return;
        if (job.remaining().isEmpty()) {
            // A top log can land on the uncut trunk. Finish the observed blocks first so
            // their drops can fall to ground; success still requires physical collection.
            if (!drops.isEmpty()) { collectDrop(maid, now); return; }
            if (job.skipped > 0) fail("tree_partly_unreachable");
            else if (missingDrops > 0 || !pendingDrops.isEmpty()) fail("drops_missing");
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
        if (!canStorePendingDrops(maid, new ItemStack(expected.getBlock().asItem()))) { fail("inventory_full"); return; }
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
            for (var drop : level.getEntitiesOfClass(ItemEntity.class, bounds)) if (!before.contains(drop)) {
                drops.add(drop); pendingDrops.add(drop.getItem().copy());
            }
        }
        job.complete(target, broken); stop(maid);
    }
    private boolean canStorePendingDrops(AiMaidEntity maid, ItemStack next) {
        // Reserve room for items already dropped but not yet picked up.
        var inventory = maid.getInventory();
        var capacity = new SimpleContainer(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) capacity.setItem(slot, inventory.getItem(slot).copy());
        for (var stack : pendingDrops) if (!capacity.addItem(stack.copy()).isEmpty()) return false;
        return capacity.addItem(next).isEmpty();
    }
    private void collectDrop(AiMaidEntity maid, long now) {
        var drop = drops.getFirst();
        if (!drop.isAlive()) {
            // Vanilla merging empties the removed entity's stack. The pending item ledger
            // remains until the surviving entity is actually collected, so loss still fails.
            if (!drop.getItem().isEmpty()) missingDrops++;
            drops.removeFirst(); settlingTicks = 0; stop(maid); return;
        }
        // Item entities need time to fall from a cut log. After settling, approach with
        // the same physical reach as container interaction before transferring the drop.
        if (!drop.onGround() && ++settlingTicks <= 40) return;
        if (!approach(maid, drop.blockPosition(), 2.5, now, "drops_unreachable")) return;
        if (drop.hasPickUpDelay()) return;
        var stack = drop.getItem();
        int pending = pendingDrops.stream().filter(s -> ItemStack.isSameItemSameComponents(s, stack)).mapToInt(ItemStack::getCount).sum();
        int requested = Math.min(pending, stack.getCount());
        var remainder = maid.getInventory().addItem(stack.copyWithCount(requested));
        int moved = requested - remainder.getCount(); collected += moved;
        int toAccount = moved;
        for (var expected : pendingDrops) if (ItemStack.isSameItemSameComponents(expected, stack)) {
            int count = Math.min(toAccount, expected.getCount());
            expected.shrink(count); toAccount -= count;
        }
        pendingDrops.removeIf(ItemStack::isEmpty);
        var left = stack.copyWithCount(stack.getCount() - moved);
        if (left.isEmpty()) drop.discard(); else drop.setItem(left);
        if (!remainder.isEmpty()) { fail("inventory_full"); return; }
        // A merged stack can contain older, unrelated items. Leave that surplus in the world.
        drops.removeFirst(); settlingTicks = 0; stop(maid);
    }
    @Override public void stop(AiMaidEntity maid) {
        if (maid.level() instanceof ServerLevel level && target != null) level.destroyBlockProgress(maid.getId(), target, -1);
        maid.stopAnimationAction(LumberAnimationIds.LUMBER, animationToken);
        breakingProgress = 0; toolSnapshot = ItemStack.EMPTY; super.stop(maid);
    }
}

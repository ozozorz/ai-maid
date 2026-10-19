package io.github.ozozorz.aimaid.farming;

import java.util.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidWorkNavigation;
import io.github.ozozorz.aimaid.entity.inventory.MaidContainers;
import io.github.ozozorz.aimaid.entity.task.MaidTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gamerules.GameRules;

/** First finite task: collect enough wheat, deposit an exact amount, physically return. */
public final class WheatDeliveryTask implements MaidTask {
    public static final int MAX_COUNT = 64, SEARCH_RADIUS = 16, DESTINATION_RANGE = 32;
    public enum Step { COLLECT, DEPOSIT, RETURN }
    private final UUID id = UUID.randomUUID(), ownerId;
    private final GlobalPos center, destination;
    private final BlockEntity destinationEntity;
    private final int requested;
    private final Set<BlockPos> unreachable = new HashSet<>();
    private final MaidWorkNavigation.Progress navigation = new MaidWorkNavigation.Progress();
    private State state = State.RUNNING;
    private Step step = Step.COLLECT;
    private String reason = "";
    private int harvested, delivered, interaction, activeTicks, stepTicks;
    private BlockPos target, feet;
    private Container opened;

    WheatDeliveryTask(AiMaidEntity maid, int requested, BlockPos chest) {
        if (requested < 1 || requested > MAX_COUNT || maid.getOwner() == null) throw new IllegalArgumentException();
        var level = MaidServerAccess.requireServerThread(maid);
        this.ownerId = maid.getOwner().getUUID();
        this.requested = requested;
        this.center = GlobalPos.of(level.dimension(), maid.blockPosition());
        this.destination = GlobalPos.of(level.dimension(), chest.immutable());
        this.destinationEntity = level.getBlockEntity(chest);
    }

    public static int wheat(AiMaidEntity maid) { return maid.getInventory().countMatching(s -> s.is(Items.WHEAT)); }
    public boolean sameRequest(int count, BlockPos chest) { return requested == count && destination.pos().equals(chest); }
    public boolean active() { return !state.terminal(); }

    @Override public Snapshot snapshot(AiMaidEntity maid) {
        return new Snapshot(id, FarmingAddon.id("deliver_wheat").toString(), state, step.name().toLowerCase(Locale.ROOT),
                Map.of("requested", requested, "available", wheat(maid), "harvested", harvested, "delivered", delivered), reason, destination);
    }

    @Override public Component status(AiMaidEntity maid) {
        return Component.translatable("status.ai-maid-farming.delivery.progress",
                Component.translatable("status.ai-maid-farming.delivery.state." + state.name().toLowerCase(Locale.ROOT)),
                Component.translatable("status.ai-maid-farming.delivery.step." + step.name().toLowerCase(Locale.ROOT)),
                wheat(maid), requested, delivered, requested,
                reason.isEmpty() ? Component.empty() : Component.translatable("status.ai-maid-farming.delivery.reason." + reason));
    }

    public void tick(AiMaidEntity maid, long now) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (state != State.RUNNING) return;
        var owner = maid.getOwner();
        if (!maid.isAlive() || !maid.isTame() || owner == null || !owner.isAlive() || owner.isSpectator()
                || !owner.getUUID().equals(ownerId) || owner.level() != level || level.dimension() != center.dimension()) {
            finish(maid, State.CANCELLED, "owner_unavailable"); return;
        }
        if (++activeTicks > 6000 || ++stepTicks > 2400) { fail(maid, "timeout"); return; }
        if (maid.isPassenger() || maid.isLeashed()) { fail(maid, "restrained"); return; }
        if (!maid.onGround()) return;
        switch (step) {
            case COLLECT -> collect(level, maid, now);
            case DEPOSIT -> deposit(level, maid, now);
            case RETURN -> {
                if (MaidWorkNavigation.canReach(maid, owner, 3.0)) {
                    if (delivered == requested) finish(maid, State.SUCCEEDED, "completed");
                    else fail(maid, "inventory_changed");
                } else {
                    // Re-read the owner's current position; never teleport to a saved position.
                    if (target == null || !target.equals(owner.blockPosition())) {
                        clearInteraction(maid); target = owner.blockPosition();
                    }
                    approach(maid, target, 2.5, now, "owner_unreachable");
                }
            }
        }
    }

    private void collect(ServerLevel level, AiMaidEntity maid, long now) {
        if (wheat(maid) >= requested) { advance(maid, Step.DEPOSIT); return; }
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) { fail(maid, "disabled"); return; }
        if (target != null && !isMatureWheat(level, target)) clearInteraction(maid);
        if (target == null) {
            var candidates = matureWheat(level, center.pos());
            candidates.removeAll(unreachable);
            candidates.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(maid.position())));
            if (candidates.isEmpty()) { fail(maid, unreachable.isEmpty() ? "insufficient_wheat" : "crop_unreachable"); return; }
            var batch = candidates.stream().limit(16).toList();
            var chosen = MaidWorkNavigation.chooseWorkPosition(maid, batch, 3.0);
            if (chosen.isEmpty()) { unreachable.addAll(batch); return; }
            target = chosen.get().target(); feet = chosen.get().feet(); navigation.reset(maid, now);
        }
        if (!MaidWorkNavigation.canReach(maid, target, 3.0)) {
            if (navigation.stalled(maid, now, 100)) {
                unreachable.add(target); clearInteraction(maid); return;
            }
            if (feet == null || !MaidWorkNavigation.canStand(maid, feet)) {
                var stand = MaidWorkNavigation.chooseStand(maid, target, 3.0);
                if (stand.isEmpty()) { unreachable.add(target); clearInteraction(maid); return; }
                feet = stand.get();
            }
            interaction = 0; MaidWorkNavigation.walk(maid, feet, target); return;
        }
        MaidWorkNavigation.haltMovement(maid); MaidWorkNavigation.lookAt(maid, target);
        if (interaction++ == 0) maid.swing(InteractionHand.MAIN_HAND);
        if (interaction < 6) return;
        // A target removed/harvested by someone else is never replanted or counted as our harvest.
        if (!isMatureWheat(level, target)) { clearInteraction(maid); return; }
        int before = wheat(maid);
        String result = FarmingActions.cultivate(level, maid, target);
        harvested += Math.max(0, wheat(maid) - before);
        clearInteraction(maid);
        if (!result.equals("working")) fail(maid, result.equals("full") ? "inventory_full" : result);
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

    private void deposit(ServerLevel level, AiMaidEntity maid, long now) {
        if (!level.hasChunkAt(destination.pos()) || destinationEntity == null
                || level.getBlockEntity(destination.pos()) != destinationEntity) { fail(maid, "chest_changed"); return; }
        var chest = MaidContainers.resolveChest(level, destination.pos());
        if (chest == null) { fail(maid, "chest_unavailable"); return; }
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) { fail(maid, "disabled"); return; }
        if (wheat(maid) < requested - delivered) { fail(maid, "inventory_changed"); return; }
        if (!approach(maid, destination.pos(), 2.5, now, "chest_unreachable")) return;
        if (chest.getEntitiesWithContainerOpen().stream().anyMatch(e -> e != maid)) {
            close(maid); interaction = 0; reason = "chest_busy"; return;
        }
        reason = "";
        if (opened == null) { chest.startOpen(maid); opened = chest; maid.setOpenedContainerPos(destination.pos()); }
        if (interaction++ == 0) maid.swing(InteractionHand.MAIN_HAND);
        if (interaction < 20) return;
        // Re-resolved, reachable, unlocked chest and current inventory; no copied cargo or delayed commit.
        for (int slot = 0; slot < maid.getInventory().getContainerSize() && delivered < requested; slot++) {
            var stack = maid.getInventory().getItem(slot);
            if (stack.is(Items.WHEAT)) delivered += MaidContainers.insert(chest, stack, requested - delivered);
        }
        maid.getInventory().setChanged();
        maid.swing(InteractionHand.MAIN_HAND);
        if (delivered < requested) fail(maid, "chest_full");
        else advance(maid, Step.RETURN);
    }

    private boolean approach(AiMaidEntity maid, BlockPos pos, double reach, long now, String failure) {
        if (MaidWorkNavigation.canReach(maid, pos, reach)) {
            MaidWorkNavigation.haltMovement(maid); MaidWorkNavigation.lookAt(maid, pos); navigation.reset(maid, now); return true;
        }
        if (target == null || !target.equals(pos)) { clearInteraction(maid); target = pos; }
        if (feet == null || !MaidWorkNavigation.canStand(maid, feet)
                || maid.getNavigation().isDone() && feet.distToCenterSqr(maid.position()) < 2) {
            var stand = MaidWorkNavigation.chooseStand(maid, pos, reach);
            if (stand.isEmpty()) { fail(maid, failure); return false; }
            feet = stand.get();
        }
        if (navigation.stalled(maid, now, 100)) { fail(maid, failure); return false; }
        close(maid); interaction = 0; MaidWorkNavigation.walk(maid, feet, pos); return false;
    }

    private void advance(AiMaidEntity maid, Step next) {
        clearInteraction(maid); step = next; stepTicks = 0; reason = "";
        navigation.reset(maid, maid.level().getGameTime());
    }
    private void fail(AiMaidEntity maid, String why) { finish(maid, State.FAILED, why); }
    private void finish(AiMaidEntity maid, State outcome, String why) {
        if (state.terminal()) return;
        state = outcome; reason = why; clearInteraction(maid);
        if (maid.getOwner() instanceof ServerPlayer owner && owner.connection != null && owner.getUUID().equals(ownerId))
            owner.sendSystemMessage(Component.translatable("message.ai-maid-farming.delivery.finished", maid.getDisplayName(), status(maid)));
    }
    @Override public void cancel(AiMaidEntity maid, String reason) { finish(maid, State.CANCELLED, reason); }
    public void pause(AiMaidEntity maid) { if (active()) { state = State.PAUSED; reason = "combat"; clearInteraction(maid); } }
    public void resume(AiMaidEntity maid) {
        if (state == State.PAUSED) { state = State.RUNNING; reason = ""; navigation.reset(maid, maid.level().getGameTime()); }
    }
    private void close(AiMaidEntity maid) {
        if (opened != null) { opened.stopOpen(maid); opened = null; }
        maid.setOpenedContainerPos(null);
    }
    public void clearInteraction(AiMaidEntity maid) {
        close(maid); MaidWorkNavigation.stop(maid); target = feet = null; interaction = 0;
    }
}

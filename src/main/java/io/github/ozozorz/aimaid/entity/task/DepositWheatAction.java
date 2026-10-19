package io.github.ozozorz.aimaid.entity.task;

import java.util.Map;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import io.github.ozozorz.aimaid.entity.inventory.MaidContainers;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;

final class DepositWheatAction extends WalkingMaidAction {
    private final int requested;
    private final MaidActions.ChestTarget destination;
    private int delivered;
    private Container opened;
    DepositWheatAction(int requested, MaidActions.ChestTarget destination) { this.requested = requested; this.destination = destination; }
    @Override public Result result(AiMaidEntity maid) {
        return new Result(state, reason, Map.of("requested", requested, "delivered", delivered, "remaining", requested - delivered));
    }
    @Override public void tick(AiMaidEntity maid, long now) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (state.terminal()) return;
        var pos = destination.position().pos();
        if (level.dimension() != destination.position().dimension() || !level.hasChunkAt(pos)
                || level.getBlockEntity(pos) != destination.entity()) { fail("chest_changed"); return; }
        var chest = MaidContainers.resolveChest(level, pos);
        if (chest == null) { fail("chest_unavailable"); return; }
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) { fail("disabled"); return; }
        if (maid.getInventory().countMatching(s -> s.is(Items.WHEAT)) < requested - delivered) { fail("inventory_changed"); return; }
        if (!approach(maid, pos, 2.5, now, "chest_unreachable")) { close(maid); return; }
        if (chest.getEntitiesWithContainerOpen().stream().anyMatch(e -> e != maid)) {
            close(maid); interaction = 0; reason = "chest_busy"; return;
        }
        reason = "";
        if (opened == null) { chest.startOpen(maid); opened = chest; maid.setOpenedContainerPos(pos); }
        if (interaction++ == 0) maid.swing(InteractionHand.MAIN_HAND);
        if (interaction < 20) return;
        for (int slot = 0; slot < maid.getInventory().getContainerSize() && delivered < requested; slot++) {
            var stack = maid.getInventory().getItem(slot);
            if (stack.is(Items.WHEAT)) delivered += MaidContainers.insert(chest, stack, requested - delivered);
        }
        maid.getInventory().setChanged(); maid.swing(InteractionHand.MAIN_HAND);
        if (delivered < requested) fail("chest_full");
        else state = MaidTask.State.SUCCEEDED;
    }
    private void close(AiMaidEntity maid) {
        if (opened != null) { opened.stopOpen(maid); opened = null; }
        maid.setOpenedContainerPos(null);
    }
    @Override public void stop(AiMaidEntity maid) { close(maid); super.stop(maid); }
}

package io.github.ozozorz.aimaid.entity.task;

import java.util.Map;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import io.github.ozozorz.aimaid.entity.inventory.MaidItemTransfer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

final class EquipToolAction implements MaidAction {
    private final int slot;
    private final Identifier item;
    private MaidTask.State state = MaidTask.State.RUNNING;
    private String reason = "";
    EquipToolAction(int slot, Identifier item) { this.slot = slot; this.item = item; }
    @Override public Result result(AiMaidEntity maid) { return new Result(state, reason, Map.of("equipped", state == MaidTask.State.SUCCEEDED ? 1 : 0)); }
    @Override public void tick(AiMaidEntity maid, long now) {
        MaidServerAccess.requireServerThread(maid);
        if (state.terminal()) return;
        var source = maid.getInventory().getItem(slot);
        if (source.isEmpty() || !BuiltInRegistries.ITEM.getKey(source.getItem()).equals(item)) { fail("inventory_changed"); return; }
        if (!source.has(DataComponents.TOOL)) { fail("not_a_tool"); return; }
        if (!MaidItemTransfer.swapInventoryWithEquipment(maid, slot, EquipmentSlot.MAINHAND)) { fail("equipment_blocked"); return; }
        state = MaidTask.State.SUCCEEDED;
    }
    private void fail(String why) { state = MaidTask.State.FAILED; reason = why; }
    @Override public void stop(AiMaidEntity maid) {}
}

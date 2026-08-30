package io.github.ozozorz.aimaid.entity.inventory;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Predicate;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

// 确保装备
public class MaidEquipmentHelper {

    private MaidEquipmentHelper() {
    }

    // 方法结束以后，这个 EquipmentSlot 是否满足要求？
    // 如果满足要求直接返回真
    // 如果不满足，就尝试在背包里找物品，找到就交换，找不到就返回false
    public static boolean ensureEquipment(AiMaidEntity maid, EquipmentSlot equipmentSlot,
            Predicate<ItemStack> predicate) {
        Objects.requireNonNull(maid);
        Objects.requireNonNull(equipmentSlot);
        Objects.requireNonNull(predicate);
        if (!MaidItemTransfer.supportsEquipmentSlot(equipmentSlot)) {
            return false;
        }

        ItemStack equipped = maid.getItemBySlot(equipmentSlot);
        // 已经满足要求，什么都不动
        if (!equipped.isEmpty() && predicate.test(equipped)) {
            return true;
        }

        OptionalInt inventorySlot = maid.getInventory().findFirstMatchingSlot(
                stack -> predicate.test(stack) && MaidItemTransfer.canPlaceInEquipmentSlot(maid, stack, equipmentSlot));
        if (inventorySlot.isEmpty()) {
            return false;
        }
        return MaidItemTransfer.swapInventoryWithEquipment(maid, inventorySlot.getAsInt(), equipmentSlot);
    }

}

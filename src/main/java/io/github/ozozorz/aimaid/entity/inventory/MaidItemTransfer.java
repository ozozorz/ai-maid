package io.github.ozozorz.aimaid.entity.inventory;

import java.util.Objects;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class MaidItemTransfer {

    private MaidItemTransfer() {
    }

    public static int moveInventoryToEquipment(AiMaidEntity maid, int inventorySlot, EquipmentSlot equipmentSlot) {
        Objects.requireNonNull(maid);
        Objects.requireNonNull(equipmentSlot);
        MaidInventory inventory = maid.getInventory();
        Objects.checkIndex(inventorySlot, inventory.getContainerSize());
        if (!isSupportedEquipmentSlot(equipmentSlot)) {
            return 0;
        }
        ItemStack source = inventory.getItem(inventorySlot);
        if (source.isEmpty()) {
            return 0;
        }
        ItemStack equippd = maid.getItemBySlot(equipmentSlot);
        if (!equippd.isEmpty()) {
            return 0;
        }
        if (!canPlaceInEquipmentSlot(maid, source, equipmentSlot)) {
            return 0;
        }
        ItemStack removed = inventory.removeItem(inventorySlot, source.getCount());
        if (removed.isEmpty()) {
            return 0;
        }
        maid.setItemSlot(equipmentSlot, removed);
        return removed.getCount();

    }

    public static int moveEquipmentToInventory(AiMaidEntity maid, EquipmentSlot equipmentSlot) {
        Objects.requireNonNull(maid);
        Objects.requireNonNull(equipmentSlot);
        if (!isSupportedEquipmentSlot(equipmentSlot)) {
            return 0;
        }
        ItemStack equipped = maid.getItemBySlot(equipmentSlot);
        if (equipped.isEmpty()) {
            return 0;
        }
        int originalCount = equipped.getCount();
        ItemStack remainder = maid.getInventory().addItem(equipped);
        int movedCount = originalCount - remainder.getCount();
        if (movedCount <= 0) {
            return 0;
        }
        maid.setItemSlot(equipmentSlot, remainder);
        return movedCount;
    }

    private static boolean isSupportedEquipmentSlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND,
                    OFFHAND,
                    HEAD,
                    CHEST,
                    LEGS,
                    FEET ->
                true;
            default -> false;
        };
    }

    private static boolean canPlaceInEquipmentSlot(AiMaidEntity maid, ItemStack itemStack, EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND, OFFHAND -> true;
            case HEAD, CHEST, LEGS, FEET -> maid.isEquippableInSlot(itemStack, slot);
            default -> false;
        };
    }

}

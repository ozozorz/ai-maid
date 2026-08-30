package io.github.ozozorz.aimaid.entity.inventory;

import java.util.Comparator;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Predicate;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class MaidItemTransfer {

    private MaidItemTransfer() {
    }

    // 将物品从背包移到装备槽位
    public static int moveInventoryToEquipment(AiMaidEntity maid, int inventorySlot, EquipmentSlot equipmentSlot) {
        Objects.requireNonNull(maid);
        Objects.requireNonNull(equipmentSlot);
        MaidInventory inventory = maid.getInventory();
        Objects.checkIndex(inventorySlot, inventory.getContainerSize());
        if (!supportsEquipmentSlot(equipmentSlot)) {
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

    // 将物品从装备槽位移到背包
    public static int moveEquipmentToInventory(AiMaidEntity maid, EquipmentSlot equipmentSlot) {
        Objects.requireNonNull(maid);
        Objects.requireNonNull(equipmentSlot);
        if (!supportsEquipmentSlot(equipmentSlot)) {
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

    // 自动将物品转移到对应装备槽
    public static int moveInventoryToPreferredEquipmentSlot(AiMaidEntity maid, int inventorySlot) {
        Objects.requireNonNull(maid);
        MaidInventory inventory = maid.getInventory();
        Objects.checkIndex(inventorySlot, inventory.getContainerSize());
        ItemStack source = inventory.getItem(inventorySlot);
        if (source.isEmpty()) {
            return 0;
        }
        EquipmentSlot preferredSlot = maid.getEquipmentSlotForItem(source);
        if (!supportsEquipmentSlot(preferredSlot)) {
            return 0;
        }
        return moveInventoryToEquipment(maid, inventorySlot, preferredSlot);
    }

    // 自动将第一个匹配的物品转移到对应装备槽
    public static int moveFirstMatchingInventoryItemToPreferredEquipmentSlot(AiMaidEntity maid,
            Predicate<ItemStack> predicate) {
        Objects.requireNonNull(maid);
        Objects.requireNonNull(predicate);

        OptionalInt slot = maid.getInventory().findFirstMatchingSlot(predicate);
        if (slot.isEmpty()) {
            return 0;
        }
        return moveInventoryToPreferredEquipmentSlot(maid, slot.getAsInt());
    }

    // 自动将最佳匹配的物品转移到对应装备槽
    public static int moveBestMatchingInventoryItemToPreferredEquipmentSlot(AiMaidEntity maid,
            Predicate<ItemStack> predicate, Comparator<ItemStack> comparator) {
        Objects.requireNonNull(maid);
        Objects.requireNonNull(predicate);
        Objects.requireNonNull(comparator);

        OptionalInt slot = maid.getInventory().findBestMatchingSlot(predicate, comparator);
        if (slot.isEmpty()) {
            return 0;
        }
        return moveInventoryToPreferredEquipmentSlot(maid, slot.getAsInt());
    }

    // 装备槽位是不是女仆支持的槽
    public static boolean supportsEquipmentSlot(EquipmentSlot slot) {
        Objects.requireNonNull(slot);
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

    // 能否放进装备槽
    public static boolean canPlaceInEquipmentSlot(AiMaidEntity maid, ItemStack itemStack, EquipmentSlot slot) {
        Objects.requireNonNull(maid);
        Objects.requireNonNull(itemStack);
        Objects.requireNonNull(slot);

        if (!supportsEquipmentSlot(slot)) {
            return false;
        }

        if (itemStack.isEmpty()) {
            return false;
        }

        return switch (slot) {
            case MAINHAND, OFFHAND -> true;
            case HEAD, CHEST, LEGS, FEET -> maid.isEquippableInSlot(itemStack, slot);
            default -> false;
        };
    }

    // 交换背包一格的物品和装备槽
    public static boolean swapInventoryWithEquipment(AiMaidEntity maid, int inventorySlot,
            EquipmentSlot equipmentSlot) {
        // 变量合法性判断
        Objects.requireNonNull(maid);
        Objects.requireNonNull(equipmentSlot);
        MaidInventory inventory = maid.getInventory();
        Objects.checkIndex(inventorySlot, inventory.getContainerSize());
        if (!supportsEquipmentSlot(equipmentSlot)) {
            return false;
        }
        ItemStack inventoryStack = inventory.getItem(inventorySlot);
        if (inventoryStack.isEmpty()) {
            return false;
        }
        if (!canPlaceInEquipmentSlot(maid, inventoryStack, equipmentSlot)) {
            return false;
        }

        ItemStack equippedStack = maid.getItemBySlot(equipmentSlot);
        // 如果本来就是空的，直接复用移动逻辑
        if (equippedStack.isEmpty()) {
            return moveInventoryToEquipment(maid, inventorySlot, equipmentSlot) > 0;
        }

        // 交换
        // 先让inventory接住旧装备
        // 然后再正式更新 equipment
        inventory.setItem(inventorySlot, equippedStack);
        maid.setItemSlot(equipmentSlot, inventoryStack);
        return true;
    }

}

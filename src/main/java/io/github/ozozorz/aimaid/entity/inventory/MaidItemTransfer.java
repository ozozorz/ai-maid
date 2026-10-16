package io.github.ozozorz.aimaid.entity.inventory;

import java.util.Comparator;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Predicate;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class MaidItemTransfer {

    private MaidItemTransfer() {
    }

    // 将物品从背包移到装备槽位
    public static int moveInventoryToEquipment(AiMaidEntity maid, int inventorySlot, EquipmentSlot equipmentSlot) {
        MaidServerAccess.requireServerThread(maid);
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
        ItemStack removed = inventory.removeItem(inventorySlot, MaidEquipmentRules.maxCount(equipmentSlot, source));
        if (removed.isEmpty()) {
            return 0;
        }
        maid.setItemSlot(equipmentSlot, removed);
        return removed.getCount();

    }

    // 将物品从装备槽位移到背包
    public static int moveEquipmentToInventory(AiMaidEntity maid, EquipmentSlot equipmentSlot) {
        MaidServerAccess.requireServerThread(maid);
        Objects.requireNonNull(maid);
        Objects.requireNonNull(equipmentSlot);
        if (!MaidEquipmentRules.canRemove(maid, equipmentSlot, null)) {
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
        maid.stopUsingItem();
        return movedCount;
    }

    // 自动将物品转移到对应装备槽
    public static int moveInventoryToPreferredEquipmentSlot(AiMaidEntity maid, int inventorySlot) {
        MaidServerAccess.requireServerThread(maid);
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
        MaidServerAccess.requireServerThread(maid);
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
        MaidServerAccess.requireServerThread(maid);
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
        return MaidEquipmentRules.supports(slot);
    }

    // 能否放进装备槽
    public static boolean canPlaceInEquipmentSlot(AiMaidEntity maid, ItemStack itemStack, EquipmentSlot slot) {
        Objects.requireNonNull(maid);
        Objects.requireNonNull(itemStack);
        Objects.requireNonNull(slot);

        return MaidEquipmentRules.canPlace(maid, itemStack, slot);
    }

    // 交换背包一格的物品和装备槽
    public static boolean swapInventoryWithEquipment(AiMaidEntity maid, int inventorySlot,
            EquipmentSlot equipmentSlot) {
        MaidServerAccess.requireServerThread(maid);
        // 变量合法性判断
        Objects.requireNonNull(maid);
        Objects.requireNonNull(equipmentSlot);
        MaidInventory inventory = maid.getInventory();
        Objects.checkIndex(inventorySlot, inventory.getContainerSize());
        if (!MaidEquipmentRules.canRemove(maid, equipmentSlot, null)) {
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

        int limit = MaidEquipmentRules.maxCount(equipmentSlot, inventoryStack);
        if (inventoryStack.getCount() > limit) {
            // 可堆叠护甲只装备一件。先模拟剩余空间，失败时两端完全不动。
            MaidInventory trial = new MaidInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) trial.setItem(i, inventory.getItem(i).copy());
            trial.removeItem(inventorySlot, limit);
            if (!trial.addItem(equippedStack.copy()).isEmpty()) return false;
            ItemStack incoming = inventory.removeItem(inventorySlot, limit);
            inventory.addItem(equippedStack);
            maid.stopUsingItem();
            maid.setItemSlot(equipmentSlot, incoming);
            return true;
        }
        if (equippedStack.getCount() > Math.min(inventory.getMaxStackSize(), equippedStack.getMaxStackSize())) return false;
        // 交换
        // 先让inventory接住旧装备
        // 然后再正式更新 equipment
        inventory.setItem(inventorySlot, equippedStack);
        maid.stopUsingItem();
        maid.setItemSlot(equipmentSlot, inventoryStack);
        return true;
    }

    /** 战斗和工作恢复共用双手交换；不复制或重建武器。 */
    public static boolean swapHands(AiMaidEntity maid) {
        MaidServerAccess.requireServerThread(maid);
        if (!MaidEquipmentRules.canRemove(maid, EquipmentSlot.MAINHAND, null)
                || !MaidEquipmentRules.canRemove(maid, EquipmentSlot.OFFHAND, null)) return false;
        ItemStack main = maid.getMainHandItem(), off = maid.getOffhandItem();
        if (main.isEmpty() && off.isEmpty()) return false;
        if (!main.isEmpty() && main.getCount() > MaidEquipmentRules.maxCount(EquipmentSlot.OFFHAND, main)
                || !off.isEmpty() && off.getCount() > MaidEquipmentRules.maxCount(EquipmentSlot.MAINHAND, off)) return false;
        maid.stopUsingItem();
        maid.setItemSlot(EquipmentSlot.MAINHAND, off);
        maid.setItemSlot(EquipmentSlot.OFFHAND, main);
        return true;
    }

}

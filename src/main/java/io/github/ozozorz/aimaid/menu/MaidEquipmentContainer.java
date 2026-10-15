package io.github.ozozorz.aimaid.menu;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/*
 * 注意：
 *
 * 这个 Container 自己没有：
 *
 * ItemStack[]
 * List<ItemStack>
 * NonNullList<ItemStack>
 *
 * 它只是把 Container API
 * 转接到 Maid 真正的 EntityEquipment。
 */
class MaidEquipmentContainer implements Container {

    static final EquipmentSlot[] EQUIPMENT_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND
    };

    private AiMaidEntity maid;

    MaidEquipmentContainer(AiMaidEntity maid) {
        this.maid = maid;
    }

    static EquipmentSlot getEquipmentSlot(int index) {
        return EQUIPMENT_SLOTS[index];
    }

    @Override
    public int getContainerSize() {
        return EQUIPMENT_SLOTS.length;
    }

    @Override
    public boolean isEmpty() {
        for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
            if (!maid.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return maid.getItemBySlot(getEquipmentSlot(index));
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        EquipmentSlot slot = getEquipmentSlot(index);
        ItemStack current = maid.getItemBySlot(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 整个 stack 被拿走
        if (count >= current.getCount()) {
            maid.setItemSlot(slot, ItemStack.EMPTY);
            return current;
        }

        // MAINHAND / OFFHAND 允许持有普通 stack，所以这里还要支持部分拆分。
        // split() 直接修改 EntityEquipment 里面那个真实 ItemStack 对象。
        return current.split(count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        EquipmentSlot slot = getEquipmentSlot(index);
        ItemStack current = maid.getItemBySlot(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        maid.setItemSlot(slot, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        maid.setItemSlot(getEquipmentSlot(index), stack);
    }

    @Override
    public void setChanged() {
        // 没有自己的数据需要 dirty。
        // EntityEquipment 的实际变化会由 LivingEntity 自己检测。
    }

    @Override
    public boolean stillValid(Player player) {
        return maid.isAlive();
    }

    @Override
    public void clearContent() {
        for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
            maid.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

}

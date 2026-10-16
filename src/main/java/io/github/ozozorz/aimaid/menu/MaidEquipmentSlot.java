package io.github.ozozorz.aimaid.menu;

import org.jspecify.annotations.Nullable;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.inventory.MaidItemTransfer;
import io.github.ozozorz.aimaid.entity.inventory.MaidEquipmentRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

class MaidEquipmentSlot extends Slot {

    private EquipmentSlot equipmentSlot;

    // Server Menu：真正的 Maid。
    // Client Menu：null。
    // Client 的 mayPlace 只是 UX prediction，最终权限仍由服务器自己的 Slot 判断。
    private @Nullable AiMaidEntity maid;

    MaidEquipmentSlot(Container container, int containerSlot, EquipmentSlot equipmentSlot, @Nullable AiMaidEntity maid, int x, int y) {
        super(container, containerSlot, x, y);
        this.equipmentSlot = equipmentSlot;
        this.maid = maid;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        // Server：直接复用已经验证过的 Core equipment mechanism。
        if (maid != null) {
            return MaidItemTransfer.canPlaceInEquipmentSlot(maid, stack, equipmentSlot);
        }

        // Client prediction。手部槽允许普通 ItemStack。
        if (equipmentSlot == EquipmentSlot.MAINHAND || equipmentSlot == EquipmentSlot.OFFHAND) {
            return true;
        }

        // Armor：客户端至少根据 EQUIPPABLE component 判断它声明的槽位。
        // 最终 allowed_entities 等规则仍由服务器的 Maid.isEquippableInSlot() 权威验证。
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == equipmentSlot;
    }

    @Override
    public int getMaxStackSize() {
        // 和 Vanilla ArmorSlot 一样：护甲槽最多一个。
        // MAINHAND / OFFHAND 则保留现有 EntityEquipment 的 stack 语义。
        return MaidEquipmentRules.slotLimit(equipmentSlot);
    }

    @Override
    public boolean mayPickup(Player player) {
        return (maid == null ? MaidEquipmentRules.canRemove(getItem(), player)
                : MaidEquipmentRules.canRemove(maid, equipmentSlot, player)) && super.mayPickup(player);
    }

    @Override
    public boolean isActive() {
        return maid == null || maid.canUseSlot(equipmentSlot);
    }

    @Override
    public @Nullable Identifier getNoItemIcon() {
        return switch (equipmentSlot) {
            case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
            case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
            case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
            case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
            // MAIN / OFFHAND 待会直接在背景上画：“主” “副”
            default -> null;
        };
    }

    private boolean isArmorSlot() {
        return switch (equipmentSlot) {
            case HEAD, CHEST, LEGS, FEET -> true;
            default -> false;
        };
    }

    

}

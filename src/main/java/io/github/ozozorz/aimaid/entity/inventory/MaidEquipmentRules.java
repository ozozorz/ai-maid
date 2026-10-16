package io.github.ozozorz.aimaid.entity.inventory;

import org.jspecify.annotations.Nullable;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** GUI 与自动换装共用的纯查询。actor=null 表示 AI；玩家授权由交互入口负责。 */
public final class MaidEquipmentRules {
    private MaidEquipmentRules() {}

    public static boolean supports(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND, OFFHAND, HEAD, CHEST, LEGS, FEET -> true;
            default -> false;
        };
    }

    public static int slotLimit(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND, OFFHAND -> 64;
            case HEAD, CHEST, LEGS, FEET -> 1;
            default -> 0;
        };
    }

    public static int maxCount(EquipmentSlot slot, ItemStack stack) {
        return Math.min(slotLimit(slot), stack.getMaxStackSize());
    }

    public static boolean canPlace(AiMaidEntity maid, ItemStack stack, EquipmentSlot slot) {
        return maid.isAlive() && supports(slot) && maid.canUseSlot(slot) && !stack.isEmpty()
                && (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND
                    || maid.isEquippableInSlot(stack, slot));
    }

    public static boolean canRemove(ItemStack stack, @Nullable Player actor) {
        // 六槽采用同一限制；创造模式玩家例外。死亡处理不经过换装规则。
        return stack.isEmpty() || actor != null && actor.isCreative()
                || !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
    }

    public static boolean canRemove(AiMaidEntity maid, EquipmentSlot slot, @Nullable Player actor) {
        return maid.isAlive() && supports(slot) && maid.canUseSlot(slot) && canRemove(maid.getItemBySlot(slot), actor);
    }
}

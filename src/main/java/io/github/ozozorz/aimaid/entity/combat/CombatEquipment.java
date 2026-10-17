package io.github.ozozorz.aimaid.entity.combat;

import java.util.function.Predicate;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.inventory.MaidItemTransfer;
import io.github.ozozorz.aimaid.entity.inventory.MaidEquipmentRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.tags.ItemTags;

/** Core 的自动武器选择；只操作真实装备和背包栈。 */
public final class CombatEquipment {
    private CombatEquipment() {}

    public static boolean isRanged(ItemStack stack) {
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    public static boolean isMelee(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof ProjectileWeaponItem)
                && stack.has(DataComponents.WEAPON);
    }

    public static boolean canShoot(AiMaidEntity maid, ItemStack weapon) {
        if (!isRanged(weapon)) return false;
        if (weapon.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(weapon)) {
            // PvE 自动射击只使用箭矢，不发射范围伤害烟花。
            return weapon.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)
                    .itemCopies().stream().allMatch(stack -> stack.is(ItemTags.ARROWS));
        }
        return maid.getProjectile(weapon).is(ItemTags.ARROWS);
    }

    public static boolean selectWeapon(AiMaidEntity maid, double distanceSqr) {
        ItemStack held = maid.getMainHandItem();
        if (!MaidEquipmentRules.canRemove(maid, EquipmentSlot.MAINHAND, null)) return false;
        // 两个切换距离避免目标在边界移动时反复换武器。
        boolean preferRanged = distanceSqr > (isRanged(held) ? 9.0 : 36.0);
        Predicate<ItemStack> ranged = stack -> canShoot(maid, stack);
        Predicate<ItemStack> melee = CombatEquipment::isMelee;
        Predicate<ItemStack> preferred = preferRanged ? ranged : melee;
        Predicate<ItemStack> fallback = preferRanged ? melee : ranged;
        int slot = bestSlot(maid, preferred);
        if (preferred.test(held)) {
            if (slot >= 0 && !isRanged(held) && score(maid.getInventory().getItem(slot)) > score(held)) {
                return MaidItemTransfer.swapInventoryWithEquipment(maid, slot, EquipmentSlot.MAINHAND);
            }
            return false;
        }
        if (slot >= 0) return MaidItemTransfer.swapInventoryWithEquipment(maid, slot, EquipmentSlot.MAINHAND);
        if (swapOffhand(maid, preferred)) return true;
        if (fallback.test(held)) return false;
        slot = bestSlot(maid, fallback);
        if (slot >= 0) return MaidItemTransfer.swapInventoryWithEquipment(maid, slot, EquipmentSlot.MAINHAND);
        return swapOffhand(maid, fallback);
    }

    private static int bestSlot(AiMaidEntity maid, Predicate<ItemStack> predicate) {
        int best = -1;
        for (int slot = 0; slot < maid.getInventory().getContainerSize(); slot++) {
            var stack = maid.getInventory().getItem(slot);
            if (stack.isEmpty() || !maid.getMaidCommand().canUseInventoryItem(maid, slot) || !predicate.test(stack)) continue;
            if (best < 0 || score(stack) > score(maid.getInventory().getItem(best))) best = slot;
        }
        return best;
    }

    static boolean swapOffhand(AiMaidEntity maid, Predicate<ItemStack> predicate) {
        ItemStack offhand = maid.getOffhandItem();
        return predicate.test(offhand) && MaidItemTransfer.swapHands(maid);
    }

    private static double score(ItemStack stack) {
        // 按主手攻击属性比较，不依赖具体铁剑/钻石剑 ID。
        double[] values = {2.0, 0.0, 1.0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                switch (modifier.operation()) {
                    case ADD_VALUE -> values[0] += modifier.amount();
                    case ADD_MULTIPLIED_BASE -> values[1] += modifier.amount();
                    case ADD_MULTIPLIED_TOTAL -> values[2] *= 1.0 + modifier.amount();
                }
            }
        });
        return (values[0] + values[0] * values[1]) * values[2];
    }
}

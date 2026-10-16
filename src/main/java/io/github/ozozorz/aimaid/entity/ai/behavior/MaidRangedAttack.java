package io.github.ozozorz.aimaid.entity.ai.behavior;

import io.github.ozozorz.aimaid.entity.MaidServerAccess;

import java.util.List;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.animation.MaidAnimationAction;
import io.github.ozozorz.aimaid.mixin.ProjectileWeaponItemInvoker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/** 执行一次真实远程攻击；是否攻击、何时攻击由 Addon 判断。 */
public final class MaidRangedAttack {
    private MaidRangedAttack() {}

    public static boolean shootBow(ServerLevel level, AiMaidEntity maid, LivingEntity target, int drawTicks) {
        MaidServerAccess.requireServerThread(maid);
        if (maid.level() != level || target.level() != level || !maid.isAlive() || !target.isAlive()) return false;
        ItemStack weapon = maid.getMainHandItem();
        if (!(weapon.getItem() instanceof BowItem bow) || drawTicks < BowItem.MAX_DRAW_DURATION) {
            return false;
        }
        ItemStack ammunition = maid.getProjectile(weapon);
        List<ItemStack> projectiles = ProjectileWeaponItemInvoker.aimaid$draw(weapon, ammunition, maid);
        if (projectiles.isEmpty()) {
            return false;
        }
        // BowItem 按朝向发射，使用与原版生物射箭相同形式的高度补偿。
        double dx = target.getX() - maid.getX();
        double dz = target.getZ() - maid.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = target.getY(0.3333333333) - (maid.getEyeY() - 0.1) + horizontal * 0.12;
        maid.setYRot((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        maid.setYHeadRot(maid.getYRot());
        maid.setXRot((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
        ((ProjectileWeaponItemInvoker) bow).aimaid$shoot(level, maid, InteractionHand.MAIN_HAND,
                weapon, projectiles, 3.0F, 0.5F, true, target);
        level.playSound(null, maid.getX(), maid.getY(), maid.getZ(), SoundEvents.ARROW_SHOOT,
                maid.getSoundSource(), 1.0F, 1.0F);
        maid.getInventory().setChanged();
        maid.playAnimationAction(MaidAnimationAction.BOW_RELEASE, 8);
        return true;
    }

    public static boolean shootCrossbow(ServerLevel level, AiMaidEntity maid, LivingEntity target) {
        MaidServerAccess.requireServerThread(maid);
        if (maid.level() != level || target.level() != level || !maid.isAlive() || !target.isAlive()) return false;
        ItemStack weapon = maid.getMainHandItem();
        if (!(weapon.getItem() instanceof CrossbowItem crossbow) || !CrossbowItem.isCharged(weapon)) {
            return false;
        }
        crossbow.performShooting(level, maid, InteractionHand.MAIN_HAND, weapon, 3.15F, 0.5F, target);
        maid.getInventory().setChanged();
        maid.playAnimationAction(MaidAnimationAction.CROSSBOW_FIRE, 6);
        return true;
    }
}

package io.github.ozozorz.aimaid.entity.combat;

import java.util.Map;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidRangedAttack;
import io.github.ozozorz.aimaid.entity.combat.CombatEquipment;
import io.github.ozozorz.aimaid.entity.combat.CombatRules;
import io.github.ozozorz.aimaid.entity.combat.WorkDefense;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** 每只 Brain 创建一个实例，临时蓄力状态不会在女仆之间共享。 */
public class MaidCombatBehavior extends Behavior<AiMaidEntity> {
    private LivingEntity lastTarget;
    private ItemStack drawingWeapon = ItemStack.EMPTY;

    public MaidCombatBehavior() {
        super(Map.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.REGISTERED,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED));
    }

    @Override
    protected boolean timedOut(long time) { return false; }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, AiMaidEntity maid) {
        return canStillUse(level, maid, level.getGameTime());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, AiMaidEntity maid, long time) {
        return CombatRules.isEnabled(maid) && maid.getBrain().isActive(ModActivities.FIGHT)
                && maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                .filter(target -> CombatRules.canTarget(maid, target)).isPresent();
    }

    @Override
    protected void tick(ServerLevel level, AiMaidEntity maid, long time) {
        LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElseThrow();
        if (lastTarget != target || maid.isUsingItem() && drawingWeapon != maid.getMainHandItem()) {
            boolean wasUsing = maid.isUsingItem();
            maid.stopUsingItem();
            drawingWeapon = ItemStack.EMPTY;
            lastTarget = target;
            // 停止与重新使用分在不同 tick，让客户端也收到停止状态。
            if (wasUsing) return;
        }
        lastTarget = target;
        maid.setAggressive(true);
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        double distanceSqr = maid.distanceToSqr(target);
        if (!maid.isUsingItem() && CombatEquipment.selectWeapon(maid, distanceSqr)) {
            // 等待 LivingEntity 更新装备属性之后再计算本次攻击。
            return;
        }
        ItemStack weapon = maid.getMainHandItem();
        if (CombatEquipment.canShoot(maid, weapon)) {
            ranged(level, maid, target, distanceSqr, time);
        } else {
            maid.stopUsingItem();
            melee(level, maid, target);
        }
    }

    private void melee(ServerLevel level, AiMaidEntity maid, LivingEntity target) {
        if (maid.getSensing().hasLineOfSight(target) && maid.isWithinMeleeAttackRange(target)) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            if (!coolingDown(maid)) {
                maid.swing(InteractionHand.MAIN_HAND);
                maid.doHurtTarget(level, target);
                long interval = (long) Math.ceil(20.0 / Math.max(0.1, maid.getAttributeValue(Attributes.ATTACK_SPEED)));
                cooldown(maid, Math.max(10, interval));
            }
        } else {
            approach(maid, target);
        }
    }

    private void ranged(ServerLevel level, AiMaidEntity maid, LivingEntity target, double distanceSqr, long time) {
        boolean visible = maid.getSensing().hasLineOfSight(target);
        if (!visible || distanceSqr > CombatRules.RANGED_RANGE * CombatRules.RANGED_RANGE) {
            maid.stopUsingItem();
            approach(maid, target);
            return;
        }
        if (distanceSqr < 9.0 && time % 10 == 0) {
            Vec3 retreat = DefaultRandomPos.getPosAway(maid, 4, 2, target.position());
            LivingEntity owner = CombatRules.owner(maid);
            if (retreat != null && (WorkDefense.supports(maid.getMaidCommand())
                    || owner == null || owner.position().distanceToSqr(retreat) < 400.0)) {
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(retreat, 1.0F, 1));
            }
        } else if (distanceSqr >= 9.0) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        if (!CombatRules.clearShot(maid, target) || coolingDown(maid)) {
            maid.stopUsingItem();
            return;
        }
        ItemStack weapon = maid.getMainHandItem();
        if (weapon.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(weapon)) {
            maid.stopUsingItem();
            if (MaidRangedAttack.shootCrossbow(level, maid, target)) cooldown(maid, 20);
        } else if (!maid.isUsingItem()) {
            drawingWeapon = weapon;
            maid.startUsingItem(InteractionHand.MAIN_HAND);
        } else if (weapon.getItem() instanceof BowItem && maid.getTicksUsingItem() >= 20) {
            int drawTicks = maid.getTicksUsingItem();
            maid.stopUsingItem();
            if (MaidRangedAttack.shootBow(level, maid, target, drawTicks)) cooldown(maid, 20);
        }
        // 弩的 onUseTick 由 LivingEntity 调用，装填和弹药消耗由 Vanilla 完成。
    }

    private static void approach(AiMaidEntity maid, LivingEntity target) {
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new EntityTracker(target, false), 1.1F, 0));
    }

    private static boolean coolingDown(AiMaidEntity maid) {
        return maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN);
    }

    private static void cooldown(AiMaidEntity maid, long ticks) {
        maid.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, ticks);
    }

    @Override
    protected void stop(ServerLevel level, AiMaidEntity maid, long time) {
        maid.stopUsingItem();
        maid.setAggressive(false);
        drawingWeapon = ItemStack.EMPTY;
        lastTarget = null;
        // 不清空 CHARGED_PROJECTILES：弩里已消耗的箭仍属于那把弩。
    }
}

package io.github.ozozorz.testaddon.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.testaddon.command.TestGuardCommand;
import io.github.ozozorz.testaddon.combat.CombatEquipment;
import io.github.ozozorz.testaddon.combat.CombatRules;
import io.github.ozozorz.testaddon.combat.WorkDefense;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.phys.Vec3;

/** 每只女仆独立观察受伤记录、追击进度和失败目标冷却。 */
public class TestGuardSensor extends Sensor<AiMaidEntity> {
    private MaidCommand previousCommand;
    private LivingEntity previousOwner;
    private int selfHurtTimestamp;
    private int ownerHurtTimestamp;
    private int ownerAttackTimestamp;
    private LivingEntity previousSelfAttacker;
    private LivingEntity previousOwnerAttacker;
    private LivingEntity previousOwnerVictim;
    private LivingEntity trackedTarget;
    private int targetPriority;
    private long targetSince;
    private long lastVisible;
    private long lastProgress;
    private double bestDistance;
    private Vec3 anchor = Vec3.ZERO;
    private boolean workInterrupted;
    private final Map<UUID, Long> retryAfter = new HashMap<>();

    public TestGuardSensor() { super(1); }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(MemoryModuleType.ATTACK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.ATTACK_COOLING_DOWN,
                MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected void doTick(ServerLevel level, AiMaidEntity maid) {
        long now = level.getGameTime();
        MaidCommand command = maid.getMaidCommand();
        LivingEntity owner = CombatRules.owner(maid);
        int selfStamp = maid.getLastHurtByMobTimestamp();
        int hurtStamp = owner == null ? 0 : owner.getLastHurtByMobTimestamp();
        int attackStamp = owner == null ? 0 : owner.getLastHurtMobTimestamp();
        LivingEntity selfAttacker = maid.getLastHurtByMob();
        LivingEntity ownerAttacker = owner == null ? null : owner.getLastHurtByMob();
        LivingEntity ownerVictim = owner == null ? null : owner.getLastHurtMob();
        boolean changed = command != previousCommand;
        boolean enabled = CombatRules.isEnabled(maid);
        boolean workCommand = WorkDefense.supports(command);
        // Vanilla 清空已死亡的攻击者也会写 timestamp；同 tick 的新攻击者可能沿用该时间值。
        // 比较事件的时间与实体身份，既不漏掉新事件，也不重放旧记录。
        boolean selfHit = enabled && !changed && selfAttacker != null
                && (selfStamp != selfHurtTimestamp || selfAttacker != previousSelfAttacker);
        boolean sameOwner = owner != null && owner == previousOwner;
        boolean ownerHit = enabled && !workCommand && !changed && sameOwner && ownerAttacker != null
                && (hurtStamp != ownerHurtTimestamp || ownerAttacker != previousOwnerAttacker);
        boolean ownerAttacked = enabled && !workCommand && !changed && sameOwner && ownerVictim != null
                && (attackStamp != ownerAttackTimestamp || ownerVictim != previousOwnerVictim);
        boolean ownerChanged = owner != previousOwner;
        boolean wasCombat = previousCommand instanceof TestGuardCommand || WorkDefense.supports(previousCommand);
        previousCommand = command;
        previousOwner = owner;
        selfHurtTimestamp = selfStamp;
        ownerHurtTimestamp = hurtStamp;
        ownerAttackTimestamp = attackStamp;
        previousSelfAttacker = selfAttacker;
        previousOwnerAttacker = ownerAttacker;
        previousOwnerVictim = ownerVictim;

        if (!enabled || changed) {
            if (wasCombat || trackedTarget != null) clearTarget(maid);
            retryAfter.clear();
            anchor = maid.position();
            return;
        }
        retryAfter.entrySet().removeIf(entry -> entry.getValue() <= now);
        LivingEntity current = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        // 目标也可能被命令选择或其他行为清掉；仍要结束旧导航、蓄力并解除暂停。
        if (current == null && trackedTarget != null) clearTarget(maid);
        if (current != null) {
            if (current != trackedTarget) track(maid, current, 3, now);
            double distance = maid.distanceToSqr(current);
            boolean visible = maid.getSensing().hasLineOfSight(current);
            if (visible) lastVisible = now;
            boolean withinAttackRange = visible && (CombatEquipment.canShoot(maid, maid.getMainHandItem())
                    ? distance <= CombatRules.RANGED_RANGE * CombatRules.RANGED_RANGE
                    : maid.isWithinMeleeAttackRange(current));
            if (withinAttackRange || distance < bestDistance - 0.25) {
                lastProgress = now;
                bestDistance = distance;
            }
            boolean pathFailed = maid.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                    .map(since -> now - since > 200).orElse(false);
            if ((!workCommand && ownerChanged) || !CombatRules.canTarget(maid, current)
                    || !CombatRules.inBounds(maid, current, anchor)
                    || now - lastVisible > CombatRules.LOST_SIGHT_TICKS
                    || now - lastProgress > CombatRules.NO_PROGRESS_TICKS
                    || now - targetSince > 1200 || pathFailed) {
                if (retryAfter.size() >= 32) retryAfter.clear();
                retryAfter.put(current.getUUID(), now + CombatRules.RETRY_TICKS);
                clearTarget(maid);
                current = null;
            }
        }

        LivingEntity candidate = selfHit ? selfAttacker : null;
        int priority = 0;
        if (!eligible(maid, candidate, now)) {
            candidate = ownerHit ? ownerAttacker : null;
            priority = 1;
        }
        if (!eligible(maid, candidate, now)) {
            candidate = ownerAttacked ? ownerVictim : null;
            priority = 2;
        }
        if (eligible(maid, candidate, now) && (current == null || priority < targetPriority)) {
            acquire(maid, candidate, priority, now);
            return;
        }
        if (current != null || owner == null || now % 10 != 0
                || !(command instanceof TestGuardCommand combat) || !combat.huntsHostiles()) return;
        for (LivingEntity nearby : maid.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(List.of())) {
            if (CombatRules.isHostile(nearby) && eligible(maid, nearby, now)
                    && maid.distanceToSqr(nearby) <= CombatRules.SEARCH_RANGE * CombatRules.SEARCH_RANGE
                    && maid.getSensing().hasLineOfSight(nearby)) {
                acquire(maid, nearby, 3, now);
                return;
            }
        }
    }

    private boolean eligible(AiMaidEntity maid, LivingEntity target, long now) {
        Vec3 center = trackedTarget == null ? maid.position() : anchor;
        return CombatRules.canTarget(maid, target) && CombatRules.inBounds(maid, target, center)
                && retryAfter.getOrDefault(target.getUUID(), 0L) <= now;
    }

    private void acquire(AiMaidEntity maid, LivingEntity target, int priority, long now) {
        // Brain 切换 Activity 不会立即停止已运行的 Behavior。
        // 在 Sensor 阶段先清理砍树裂纹/动作和 MoveToTargetSink，再让本 tick 开始战斗。
        maid.getBrain().stopAll((ServerLevel) maid.level(), maid);
        if (WorkDefense.supports(maid.getMaidCommand()) && !workInterrupted) {
            WorkDefense.suspend(maid, now);
            workInterrupted = true;
        }
        maid.stopUsingItem();
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        track(maid, target, priority, now);
        maid.getBrain().setActiveActivityToFirstValid(maid.getMaidCommand().getActivityCandidates(maid));
    }

    private void track(AiMaidEntity maid, LivingEntity target, int priority, long now) {
        if (trackedTarget == null) anchor = maid.position();
        trackedTarget = target;
        targetPriority = priority;
        targetSince = lastVisible = lastProgress = now;
        bestDistance = maid.distanceToSqr(target);
    }

    private void clearTarget(AiMaidEntity maid) {
        maid.getBrain().stopAll((ServerLevel) maid.level(), maid);
        WorkDefense.clearCombat(maid);
        if (workInterrupted) WorkDefense.resume(maid, maid.level().getGameTime());
        workInterrupted = false;
        trackedTarget = null;
        maid.getBrain().setActiveActivityToFirstValid(maid.getMaidCommand().getActivityCandidates(maid));
    }
}

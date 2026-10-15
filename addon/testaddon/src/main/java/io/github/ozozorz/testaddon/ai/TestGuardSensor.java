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
    private LivingEntity trackedTarget;
    private int targetPriority;
    private long targetSince;
    private long lastVisible;
    private long lastProgress;
    private double bestDistance;
    private Vec3 anchor = Vec3.ZERO;
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
        boolean changed = command != previousCommand;
        boolean enabled = CombatRules.isEnabled(maid);
        boolean selfHit = enabled && !changed && selfStamp != selfHurtTimestamp;
        boolean sameOwner = owner != null && owner == previousOwner;
        boolean ownerHit = enabled && !changed && sameOwner && hurtStamp != ownerHurtTimestamp;
        boolean ownerAttacked = enabled && !changed && sameOwner && attackStamp != ownerAttackTimestamp;
        boolean ownerChanged = owner != previousOwner;
        boolean wasCombat = previousCommand instanceof TestGuardCommand;
        previousCommand = command;
        previousOwner = owner;
        selfHurtTimestamp = selfStamp;
        ownerHurtTimestamp = hurtStamp;
        ownerAttackTimestamp = attackStamp;

        if (!enabled || changed) {
            if (wasCombat || trackedTarget != null) clearTarget(maid);
            retryAfter.clear();
            anchor = maid.position();
            return;
        }
        retryAfter.entrySet().removeIf(entry -> entry.getValue() <= now);
        LivingEntity current = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (current == null) trackedTarget = null;
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
            if (ownerChanged || !CombatRules.canTarget(maid, current)
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

        LivingEntity candidate = selfHit ? maid.getLastHurtByMob() : null;
        int priority = 0;
        if (!eligible(maid, candidate, now)) {
            candidate = ownerHit && owner != null ? owner.getLastHurtByMob() : null;
            priority = 1;
        }
        if (!eligible(maid, candidate, now)) {
            candidate = ownerAttacked && owner != null ? owner.getLastHurtMob() : null;
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
        maid.stopUsingItem();
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        track(maid, target, priority, now);
    }

    private void track(AiMaidEntity maid, LivingEntity target, int priority, long now) {
        trackedTarget = target;
        targetPriority = priority;
        targetSince = lastVisible = lastProgress = now;
        bestDistance = maid.distanceToSqr(target);
        anchor = maid.position();
    }

    private void clearTarget(AiMaidEntity maid) {
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        maid.stopUsingItem();
        maid.setAggressive(false);
        trackedTarget = null;
    }
}

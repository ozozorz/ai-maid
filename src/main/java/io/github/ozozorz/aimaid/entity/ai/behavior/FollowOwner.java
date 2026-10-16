package io.github.ozozorz.aimaid.entity.ai.behavior;

import java.util.Map;
import com.mojang.datafixers.util.Unit;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

/** 每只 Brain 自己记录追赶进度；只有 FOLLOW_OWNER 能尝试安全归队。 */
public final class FollowOwner extends Behavior<AiMaidEntity> {
    private final float speed;
    private final double startSqr, stopSqr;
    private final int closeEnough;
    private long progressAt, retryAt;
    private double bestDistance;

    private FollowOwner(float speed, double start, double stop, int closeEnough) {
        super(Map.of(ModMemoryModuleTypes.OWNER, MemoryStatus.VALUE_PRESENT,
                ModMemoryModuleTypes.FOLLOWING_OWNER, MemoryStatus.REGISTERED,
                ModMemoryModuleTypes.FOLLOW_BLOCKED, MemoryStatus.REGISTERED,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED));
        this.speed = speed;
        this.startSqr = start * start;
        this.stopSqr = stop * stop;
        this.closeEnough = closeEnough;
    }

    public static BehaviorControl<AiMaidEntity> create(float speed, double start, double stop, int closeEnough) {
        return new FollowOwner(speed, start, stop, closeEnough);
    }

    public static boolean available(AiMaidEntity maid, LivingEntity owner) {
        return owner != null && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator() && owner.level() == maid.level();
    }

    @Override protected boolean timedOut(long now) { return false; }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, AiMaidEntity maid) {
        return canStillUse(level, maid, level.getGameTime());
    }
    @Override protected boolean canStillUse(ServerLevel level, AiMaidEntity maid, long now) {
        return maid.getBrain().isActive(ModActivities.FOLLOW_OWNER) && available(maid, maid.getOwner());
    }
    @Override protected void start(ServerLevel level, AiMaidEntity maid, long now) {
        progressAt = now;
        retryAt = now;
        bestDistance = maid.distanceToSqr(maid.getOwner());
    }

    @Override protected void tick(ServerLevel level, AiMaidEntity maid, long now) {
        LivingEntity owner = maid.getOwner();
        var brain = maid.getBrain();
        double distance = maid.distanceToSqr(owner);
        if (maid.isPassenger() || maid.isVehicle() || maid.isLeashed() || maid.isOrderedToSit()) {
            stopWalking(maid);
            brain.setMemory(ModMemoryModuleTypes.FOLLOWING_OWNER, Unit.INSTANCE);
            progressAt = now;
            return;
        }
        boolean following = brain.hasMemoryValue(ModMemoryModuleTypes.FOLLOWING_OWNER);
        if (distance <= stopSqr || !following && distance <= startSqr) {
            if (following) stopWalking(maid);
            brain.eraseMemory(ModMemoryModuleTypes.FOLLOW_BLOCKED);
            progressAt = now;
            bestDistance = distance;
            return;
        }
        if (!following || distance < bestDistance - 1) {
            progressAt = now;
            bestDistance = distance;
            brain.eraseMemory(ModMemoryModuleTypes.FOLLOW_BLOCKED);
        }
        brain.setMemory(ModMemoryModuleTypes.FOLLOWING_OWNER, Unit.INSTANCE);
        boolean pathFailed = brain.getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                .map(since -> now - since >= 100).orElse(false);
        if (pathFailed || now - progressAt >= 100) {
            brain.setMemory(ModMemoryModuleTypes.FOLLOW_BLOCKED, Unit.INSTANCE);
            if (distance >= 144 && now >= retryAt) {
                retryAt = now + 40;
                if (trySafeTeleport(level, maid, owner)) {
                    stopWalking(maid);
                    brain.eraseMemory(ModMemoryModuleTypes.FOLLOW_BLOCKED);
                    progressAt = now;
                    bestDistance = maid.distanceToSqr(owner);
                    return;
                }
            }
        }
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(owner, speed, closeEnough));
    }

    private static boolean trySafeTeleport(ServerLevel level, AiMaidEntity maid, LivingEntity owner) {
        if (!maid.onGround() || maid.isInWater() || maid.isInLava()) return false;
        BlockPos center = owner.blockPosition();
        // 原版 WALKABLE、树叶和碰撞检查，加上已加载区块、边界与流体检查。
        for (int dy = -1; dy <= 1; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            BlockPos pos = center.offset(dx, dy, dz);
            if (!level.hasChunksAt(pos.offset(-1, -1, -1), pos.offset(1, 2, 1))
                    || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()
                    || !level.getFluidState(pos.below()).isEmpty()
                    || level.getBlockState(pos.below()).getBlock() instanceof LeavesBlock
                    || WalkNodeEvaluator.getPathTypeStatic(maid, pos) != PathType.WALKABLE) continue;
            Vec3 target = Vec3.atBottomCenterOf(pos);
            var box = maid.getBoundingBox().move(target.subtract(maid.position()));
            if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(maid, box)) continue;
            maid.snapTo(target.x, target.y, target.z, maid.getYRot(), maid.getXRot());
            maid.setDeltaMovement(Vec3.ZERO);
            maid.fallDistance = 0;
            return true;
        }
        return false;
    }

    private static void stopWalking(AiMaidEntity maid) {
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        maid.getBrain().eraseMemory(ModMemoryModuleTypes.FOLLOWING_OWNER);
    }
    @Override protected void stop(ServerLevel level, AiMaidEntity maid, long now) {
        stopWalking(maid);
        maid.getBrain().eraseMemory(ModMemoryModuleTypes.FOLLOW_BLOCKED);
    }

    public static Component status(AiMaidEntity maid) {
        if (!available(maid, maid.getOwner())) return Component.translatable("status.ai-maid.waiting_owner");
        if (maid.isPassenger() || maid.isVehicle() || maid.isLeashed() || maid.isOrderedToSit())
            return Component.translatable("status.ai-maid.follow_restrained");
        if (maid.getBrain().hasMemoryValue(ModMemoryModuleTypes.FOLLOW_BLOCKED))
            return Component.translatable("status.ai-maid.follow_blocked");
        return Component.empty();
    }
}

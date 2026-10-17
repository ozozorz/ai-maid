package io.github.ozozorz.aimaid.entity.ai.behavior;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;

/** 工作暂时无事可做时看看周围、短距离走动。是否允许等待由 Addon 的当前工作阶段决定。 */
public final class MaidWorkIdle extends Behavior<AiMaidEntity> {
    private final Predicate<AiMaidEntity> allowed;
    private final Function<AiMaidEntity, GlobalPos> center;
    private final ToIntFunction<AiMaidEntity> radius;
    private WalkTarget ownWalk;
    private PositionTracker ownLook;
    private long nextLook, nextWalk;

    public MaidWorkIdle(Predicate<AiMaidEntity> allowed, Function<AiMaidEntity, GlobalPos> center,
            ToIntFunction<AiMaidEntity> radius) {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.allowed = allowed; this.center = center; this.radius = radius;
    }
    @Override protected boolean timedOut(long now) { return false; }
    private boolean available(AiMaidEntity maid) {
        GlobalPos area = center.apply(maid);
        return allowed.test(maid) && maid.isAlive() && maid.onGround() && !maid.isPassenger() && !maid.isLeashed()
                && !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && area != null && area.dimension() == maid.level().dimension();
    }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, AiMaidEntity maid) { return available(maid); }
    @Override protected boolean canStillUse(ServerLevel level, AiMaidEntity maid, long now) { return available(maid); }
    @Override protected void start(ServerLevel level, AiMaidEntity maid, long now) {
        nextWalk = now + 80 + maid.getRandom().nextInt(80);
    }
    @Override protected void tick(ServerLevel level, AiMaidEntity maid, long now) {
        var brain = maid.getBrain();
        if (now >= nextLook && !brain.hasMemoryValue(MemoryModuleType.LOOK_TARGET)) {
            var owner = maid.getOwner();
            ownLook = owner != null && owner.level() == level && maid.distanceToSqr(owner) < 64
                    ? new EntityTracker(owner, true)
                    : new BlockPosTracker(maid.blockPosition().offset(maid.getRandom().nextInt(7) - 3, 1,
                            maid.getRandom().nextInt(7) - 3));
            brain.setMemoryWithExpiry(MemoryModuleType.LOOK_TARGET, ownLook, 35);
            nextLook = now + 40 + maid.getRandom().nextInt(40);
        }
        if (now < nextWalk || brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) return;
        nextWalk = now + 100 + maid.getRandom().nextInt(80);
        BlockPos origin = center.apply(maid).pos();
        int limit = radius.applyAsInt(maid);
        // 只在同一高度缓步移动，避免工作间隙主动跳上耕地；整条路径也不能越出工作区域。
        for (int i = 0; i < 8; i++) {
            BlockPos feet = maid.blockPosition().offset(maid.getRandom().nextInt(5) - 2, 0,
                    maid.getRandom().nextInt(5) - 2);
            if (feet.distToCenterSqr(maid.position()) < 1 || !inside(feet, origin, limit)
                    || !MaidWorkNavigation.canStand(maid, feet)) continue;
            var path = maid.getNavigation().createPath(feet, 0);
            if (path == null || !path.canReach()) continue;
            boolean contained = true;
            for (int node = 0; node < path.getNodeCount(); node++) {
                var p = path.getNode(node);
                if (!inside(p.asBlockPos(), origin, limit) || p.y != feet.getY()) { contained = false; break; }
            }
            if (!contained) continue;
            ownWalk = new WalkTarget(feet, 0.45F, 0);
            brain.setMemory(MemoryModuleType.WALK_TARGET, ownWalk);
            return;
        }
    }
    private static boolean inside(BlockPos pos, BlockPos center, int radius) {
        return Math.abs(pos.getX() - center.getX()) <= radius && Math.abs(pos.getZ() - center.getZ()) <= radius;
    }
    @Override protected void stop(ServerLevel level, AiMaidEntity maid, long now) {
        // 同一个 tick 内工作可能已经接管了移动，不能抹掉它新写入的 Memory。
        if (ownWalk != null && maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == ownWalk) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getNavigation().stop();
        }
        if (ownLook != null && maid.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).orElse(null) == ownLook)
            maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        ownWalk = null; ownLook = null;
    }
}

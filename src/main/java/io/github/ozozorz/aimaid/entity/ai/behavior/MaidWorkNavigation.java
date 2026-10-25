package io.github.ozozorz.aimaid.entity.ai.behavior;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 通用的近距离方块交互与站位机制；目标选择和工作规则由 Addon 决定。 */
public final class MaidWorkNavigation {
    private MaidWorkNavigation() {}

    public static boolean canReach(AiMaidEntity maid, BlockPos target, double range) {
        return canReachFrom(maid, maid.getEyePosition(), Vec3.atCenterOf(target), target, range);
    }

    public static boolean canReach(AiMaidEntity maid, LivingEntity target, double range) {
        return target.isAlive() && target.level() == maid.level()
                && canReachFrom(maid, maid.getEyePosition(), target.getBoundingBox().getCenter(), null, range);
    }

    private static boolean canReachFrom(AiMaidEntity maid, Vec3 eye, Vec3 to, BlockPos block, double range) {
        BlockPos pos = BlockPos.containing(to);
        if (!maid.level().hasChunkAt(pos) || !maid.level().getWorldBorder().isWithinBounds(pos)
                || eye.distanceToSqr(to) > range * range) return false;
        var hit = maid.level().clip(new ClipContext(eye, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maid));
        return hit.getType() == HitResult.Type.MISS || block != null && hit.getBlockPos().equals(block);
    }

    /** 保留 1.2.0 调用入口；新调用者应明确提供自己的交互距离。 */
    public static Optional<BlockPos> chooseStand(AiMaidEntity maid, BlockPos target) {
        return chooseStand(maid, target, 3.0);
    }

    public static Optional<BlockPos> chooseStand(AiMaidEntity maid, BlockPos target, double range) {
        return chooseWorkPosition(maid, List.of(target), range).map(WorkPosition::feet);
    }

    public static Optional<BlockPos> chooseStand(AiMaidEntity maid, LivingEntity target, double range) {
        MaidServerAccess.requireServerThread(maid);
        if (canReach(maid, target, range)) return Optional.of(maid.blockPosition());
        var candidates = new LinkedHashMap<BlockPos, BlockPos>();
        collectStands(maid, target.blockPosition(), target.getBoundingBox().getCenter(), null, range, candidates);
        var path = candidates.isEmpty() ? null : maid.getNavigation().createPath(candidates.keySet(), 0);
        return path != null && path.canReach() ? Optional.of(path.getTarget()) : Optional.empty();
    }

    public record WorkPosition(BlockPos target, BlockPos feet) {}

    /** 一批候选只进行一次多目标寻路。到得了脚下位置，并不代表伸手够得到工作目标。 */
    public static Optional<WorkPosition> chooseWorkPosition(AiMaidEntity maid, List<BlockPos> targets, double range) {
        MaidServerAccess.requireServerThread(maid);
        for (BlockPos target : targets) if (canReach(maid, target, range))
            return Optional.of(new WorkPosition(target.immutable(), maid.blockPosition()));
        var candidates = new LinkedHashMap<BlockPos, BlockPos>();
        for (BlockPos target : targets) {
            collectStands(maid, target, Vec3.atCenterOf(target), target, range, candidates);
        }
        if (candidates.isEmpty()) return Optional.empty();
        var path = maid.getNavigation().createPath(candidates.keySet(), 0);
        return path != null && path.canReach()
                ? Optional.of(new WorkPosition(candidates.get(path.getTarget()), path.getTarget())) : Optional.empty();
    }

    private static void collectStands(AiMaidEntity maid, BlockPos target, Vec3 to, BlockPos hitBlock,
            double range, LinkedHashMap<BlockPos, BlockPos> candidates) {
        int radius = Math.min(3, (int) Math.ceil(range));
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            for (int y = -1; y <= 1; y++) {
                BlockPos feet = target.offset(x, y, z).immutable();
                // 按实际交互距离验证格子中心；walk 会处理进格后还需靠近中心的情况。
                // 不能统一扣除半格对角线，否则低处目标（如树根）的合法站位会全部被排除。
                Vec3 eye = Vec3.atBottomCenterOf(feet).add(0, maid.getEyeHeight(), 0);
                if (canStand(maid, feet) && canReachFrom(maid, eye, to, hitBlock, range))
                    candidates.putIfAbsent(feet, target.immutable());
            }
        }
    }

    public static boolean canStand(AiMaidEntity maid, BlockPos feet) {
        ServerLevel level = MaidServerAccess.requireServerThread(maid);
        return level.hasChunksAt(feet.offset(-1,-1,-1), feet.offset(1,2,1))
                && level.getWorldBorder().isWithinBounds(feet) && level.getFluidState(feet).isEmpty()
                && maid.getNavigation().isStableDestination(feet)
                && level.noCollision(maid, maid.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(maid.position())));
    }

    public static void walk(AiMaidEntity maid, BlockPos feet, BlockPos look) {
        MaidServerAccess.requireServerThread(maid);
        walk(maid, feet);
        lookAt(maid, look);
    }

    public static void walk(AiMaidEntity maid, BlockPos feet) {
        MaidServerAccess.requireServerThread(maid);
        // 原版 MoveToTargetSink 进入目标方格就停止。此时可能仍够不到工作目标，
        // 用正常移动控制继续靠近已验证的格子中心；是否真正到位仍由 canReach 判定。
        if (feet.equals(maid.blockPosition())) {
            haltMovement(maid);
            Vec3 center = Vec3.atBottomCenterOf(feet);
            maid.getMoveControl().setWantedPosition(center.x, center.y, center.z, 0.8);
            return;
        }
        var existing = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (existing == null || !existing.getTarget().currentBlockPosition().equals(feet)
                || existing.getSpeedModifier() != 0.8F || existing.getCloseEnoughDist() != 0) {
            // 原版 sink 对只变动一格的目标可能沿用旧路径；切目标时让它重新接管。
            maid.getNavigation().stop();
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(feet, 0.8F, 0));
        }
    }

    public static void lookAt(AiMaidEntity maid, BlockPos target) {
        MaidServerAccess.requireServerThread(maid);
        maid.getBrain().setMemoryWithExpiry(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target), 10);
    }

    public static void lookAt(AiMaidEntity maid, LivingEntity target) {
        MaidServerAccess.requireServerThread(maid);
        maid.getBrain().setMemoryWithExpiry(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true), 10);
    }

    /** 到位时只停脚，交互仍然保持朝向；退出工作时才调用 stop。 */
    public static void haltMovement(AiMaidEntity maid) {
        MaidServerAccess.requireServerThread(maid);
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    public static void stop(AiMaidEntity maid) {
        haltMovement(maid);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    /** 每次任务独立使用；缓慢但持续接近不应被固定总时长判为卡死。 */
    public static final class Progress {
        private Vec3 position;
        private long lastMovement;
        public void reset(AiMaidEntity maid, long now) { position = maid.position(); lastMovement = now; }
        public boolean stalled(AiMaidEntity maid, long now, int timeout) {
            if (position == null || position.distanceToSqr(maid.position()) >= 0.0625) reset(maid, now);
            return now - lastMovement >= timeout;
        }
    }
}

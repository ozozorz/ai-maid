package io.github.ozozorz.aimaid.lumber.lumber;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidBlockBreaking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LumberWorkPlanner {
    private static final int MAX_STATIONS = 128;
    private LumberWorkPlanner() {}
    public record Selection(BlockPos log, BlockPos feet) {}

    public static Optional<Selection> choose(ServerLevel level, AiMaidEntity maid, LumberJob job) {
        for (BlockPos log : List.copyOf(job.remaining().keySet())) {
            if (!level.hasChunkAt(log) || level.getBlockState(log) != job.remaining().get(log)) job.complete(log, false);
        }
        // 当前站位已可触及就直接作业，不重新启动一条导航。
        for (BlockPos log : job.remaining().keySet()) {
            if (maid.onGround() && MaidBlockBreaking.canReach(level, maid, log)) {
                return Optional.of(new Selection(log, maid.blockPosition().immutable()));
            }
        }
        var logByFeet = new LinkedHashMap<BlockPos, BlockPos>();
        outer: for (BlockPos log : job.remaining().keySet()) {
            // 有限的环形站位候选，允许上下地形，但绝不造台阶/隔空破坏。
            for (int radius = 0; radius <= 3; radius++) for (int dy = -5; dy <= 1; dy++) {
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos feet = log.offset(dx, dy, dz);
                    if (logByFeet.containsKey(feet) || !canStand(level, maid, feet)) continue;
                    Vec3 eye = Vec3.atBottomCenterOf(feet).add(0, maid.getEyeHeight(), 0);
                    if (!MaidBlockBreaking.canReachFrom(level, maid, eye, log)) continue;
                    logByFeet.put(feet, log);
                    if (logByFeet.size() >= MAX_STATIONS) break outer;
                }
            }
        }
        if (logByFeet.isEmpty()) {
            for (BlockPos pos : List.copyOf(job.remaining().keySet())) job.complete(pos, false);
            return Optional.empty();
        }
        var path = maid.getNavigation().createPath(logByFeet.keySet(), 0);
        if (path == null || !path.canReach()) {
            // 本批候选不可达，只跳过本批；未进入有界寻路批次的原木下 tick 再处理。
            for (BlockPos pos : logByFeet.values()) job.complete(pos, false);
            return Optional.empty();
        }
        BlockPos feet = path.getTarget().immutable();
        BlockPos log = logByFeet.get(feet);
        return log == null ? Optional.empty() : Optional.of(new Selection(log, feet));
    }

    public static boolean canStand(ServerLevel level, AiMaidEntity maid, BlockPos feet) {
        if (!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)
                || !maid.getNavigation().isStableDestination(feet)
                || !level.getFluidState(feet).isEmpty()) return false;
        Vec3 center = Vec3.atBottomCenterOf(feet);
        double halfWidth = maid.getBbWidth() / 2.0;
        return level.noCollision(maid, new AABB(center.x - halfWidth, center.y, center.z - halfWidth,
                center.x + halfWidth, center.y + maid.getBbHeight(), center.z + halfWidth));
    }
}

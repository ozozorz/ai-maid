package io.github.ozozorz.aimaid.lumber.lumber;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidBlockBreaking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
        double range = maid.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        outer: for (BlockPos log : job.remaining().keySet()) {
            // 有限的环形站位候选，允许上下地形，但绝不造台阶/隔空破坏。
            for (int radius = 0; radius <= 3; radius++) for (int dy = -5; dy <= 1; dy++) {
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos feet = log.offset(dx, dy, dz);
                    // 当前格的实际位置够不到时，不能再次选择这格并等待寻路把它当成已到达。
                    if (feet.equals(maid.blockPosition()) || logByFeet.containsKey(feet) || !canStand(level, maid, feet)) continue;
                    Vec3 eye = Vec3.atBottomCenterOf(feet).add(0, maid.getEyeHeight(), 0);
                    // MoveToTargetSink 进入目标格就会停止。按格内最远的水平角点检查距离，
                    // 避免只有中心够得到；保留真实的垂直距离，不缩短可砍树干的高度。
                    Vec3 delta = Vec3.atCenterOf(log).subtract(eye);
                    double farX = Math.abs(delta.x) + 0.5, farZ = Math.abs(delta.z) + 0.5;
                    if (farX * farX + delta.y * delta.y + farZ * farZ > range * range) continue;
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

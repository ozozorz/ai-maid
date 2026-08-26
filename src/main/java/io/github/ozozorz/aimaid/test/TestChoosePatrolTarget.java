package io.github.ozozorz.aimaid.test;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class TestChoosePatrolTarget {

    private TestChoosePatrolTarget() {
    }

    public static BehaviorControl<AiMaidEntity> create() {

        return BehaviorBuilder.create(instance -> instance.group(
                instance.absent(TestAddonMemoryModuleTypes.PATROL_TARGET)).apply(instance, patrolTarget -> {
                    return (level, maid, timestamp) -> {
                        GlobalPos center = TestAddonMaidData.getPatrolCentere(maid);

                        if (center == null) {
                            // 兼容旧存档 / 异常数据
                            // PATROL 已经激活但还没有 center
                            TestAddonMaidData.setPatrolCenter(maid);
                            return false;
                        }

                        if (!center.dimension().equals(level.dimension())) {
                            return false;
                        }

                        int radius = TestAddonMaidData.getPatrolRadius(maid);

                        Vec3 target = findPatrolPosition(maid, center.pos(), radius);

                        if (target == null) {
                            return false;
                        }

                        patrolTarget.set(BlockPos.containing(target));

                        return true;
                    };
                }));

    }

    private static Vec3 findPatrolPosition(AiMaidEntity maid, BlockPos center, int radius) {

        Vec3 centerPos = Vec3.atCenterOf(center);

        /*
         * 如果 Maid 因击退、传送等原因
         * 已经明显跑出了巡逻区，
         * 优先尝试往中心方向回来。
         */
        double outsideRadius = radius + 2.0;

        if (horizonalDistanceSqr(maid.position(), centerPos) > outsideRadius * outsideRadius) {
            return LandRandomPos.getPosTowards(maid, Math.max(radius, 8), 3, centerPos);
        }

        /*
         * 正常情况：
         * 在中心半径内随机挑一个期望位置，
         * 再让 LandRandomPos 找真正可行走的陆地点。
         */
        for (int attempt = 0; attempt < 12; attempt++) {

            double angle = maid.getRandom().nextDouble() * Math.PI * 2.0;

            /*
             * sqrt(random)
             * 会让二维圆盘中的点分布更均匀。
             */
            double distance = Math.sqrt(maid.getRandom().nextDouble()) * radius;

            Vec3 desired = new Vec3(centerPos.x + Math.cos(angle) * distance, centerPos.y,
                    centerPos.z + Math.sin(angle) * distance);

            Vec3 candicate = LandRandomPos.getPosTowards(maid, Math.max(radius + 2, 6), 3, desired);

            if (candicate == null) {
                continue;
            }

            // 最终候选必须仍然落在 patrol radius 内。
            if (horizonalDistanceSqr(candicate, centerPos) > radius * radius) {
                continue;
            }

            // 太靠近当前位置就没有巡逻感了
            if (candicate.distanceToSqr(maid.position()) < 2.25) {
                continue;
            }

            return candicate;
        }

        return null;

    }

    private static double horizonalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

}

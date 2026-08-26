package io.github.ozozorz.aimaid.test;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

public class TestWalkToPatrolTarget {

    private TestWalkToPatrolTarget() {
    }

    public static BehaviorControl<AiMaidEntity> create(float speedModifier) {
        return BehaviorBuilder.create(instance -> instance.group(
                instance.present(TestAddonMemoryModuleTypes.PATROL_TARGET),
                instance.registered(MemoryModuleType.WALK_TARGET))
                .apply(instance, (patrolTargetMemory, walkTargetMemory) -> {
                    return (level, maid, timestamp) -> {

                        BlockPos patrolTarget = instance.get(patrolTargetMemory);

                        Vec3 targetCenter = Vec3.atCenterOf(patrolTarget);

                        /*
                         * 到达本次 PATROL_TARGET。
                         */
                        if (maid.position().distanceToSqr(targetCenter) <= 2.25) {
                            patrolTargetMemory.erase();
                            walkTargetMemory.erase();
                            return true;
                        }

                        /*
                         * 已经有 WALK_TARGET 时不要覆盖。
                         */
                        if (!maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                            walkTargetMemory.set(new WalkTarget(patrolTarget, speedModifier, 1));
                        }

                        return true;
                    };
                }));
    }

}

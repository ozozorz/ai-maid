package io.github.ozozorz.testaddon.ai.behavior;

import com.mojang.datafixers.util.Unit;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class TestWalkToPatrolTarget {

    private static final int CLOSE_ENGHOU_DISTANCE = 1;

    private static final UniformInt PAUSE_TIME = UniformInt.of(40, 100);

    private TestWalkToPatrolTarget() {
    }

    public static BehaviorControl<AiMaidEntity> create(float speedModifier) {
        return BehaviorBuilder.create(instance -> instance.group(
                instance.present(TestAddonMemoryModuleTypes.PATROL_TARGET),
                instance.registered(MemoryModuleType.WALK_TARGET),
                instance.registered(TestAddonMemoryModuleTypes.PATROL_PAUSE))
                .apply(instance, (patrolTargetMemory, walkTargetMemory, patrolPauseMemory) -> {
                    return (level, maid, timestamp) -> {

                        BlockPos patrolTarget = instance.get(patrolTargetMemory);

                        boolean reached = patrolTarget.distManhattan(maid.blockPosition()) <= CLOSE_ENGHOU_DISTANCE;

                        // =====================
                        // 已经到达巡逻点
                        // =====================
                        if (reached) {
                            patrolTargetMemory.erase();
                            walkTargetMemory.erase();
                            int pauseTicks = PAUSE_TIME.sample(maid.getRandom());
                            patrolPauseMemory.setWithExpiry(Unit.INSTANCE, pauseTicks);
                            return true;
                        }

                        // =====================
                        // 还没到
                        // =====================
                        if (!maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                            walkTargetMemory.set(new WalkTarget(patrolTarget, speedModifier, CLOSE_ENGHOU_DISTANCE));
                        }

                        return true;
                    };
                }));
    }

}

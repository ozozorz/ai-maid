package io.github.ozozorz.testaddon.ai.behavior;

import java.util.Optional;

import com.mojang.datafixers.util.Unit;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.gamerules.GameRules;

public class TestWalkToLumberTarget {

    private static final int CLOSE_ENOUGH_DISTANCE = 1;

    // 10秒持续不可达后，就放弃这次工作
    private static final long MAX_CANT_REACH_TICKS = 200L;

    private TestWalkToLumberTarget() {
    }

    public static BehaviorControl<AiMaidEntity> create(float speedModifier) {
        return BehaviorBuilder.create(i ->
            i.group(
                i.present(TestAddonMemoryModuleTypes.LUMBER_TARGET),
                i.absent(TestAddonMemoryModuleTypes.LUMBER_DONE),
                i.registered(MemoryModuleType.WALK_TARGET),
                i.registered(MemoryModuleType.LOOK_TARGET),
                i.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
            ).apply(i, (lumberTarget, lumberDone, walkTarget, lookTarget, cantReachWalkTargetSince) ->
                (level, maid, timestamp) -> {

                    BlockPos targetPos = i.get(lumberTarget);

                    // 工作进行期间世界可能已经变化。
                    if (!level.getGameRules().get(GameRules.MOB_GRIEFING) || !level.getBlockState(targetPos).is(BlockTags.LOGS)) {
                        lumberDone.set(Unit.INSTANCE);
                        return true;
                    }

                    /*
                     * MoveToTargetSink 自己会维护这个 Vanilla Memory
                     * 这里只消费它，不重新发明 path-failure timer
                     */
                    Optional<Long> cantReachSince = maid.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                    
                    if (cantReachSince.isPresent() && timestamp - cantReachSince.get() > MAX_CANT_REACH_TICKS) {
                        lumberDone.set(Unit.INSTANCE);
                        return true;
                    }

                    BlockPosTracker tracker = new BlockPosTracker(targetPos);
                    
                    lookTarget.set(tracker);
                    
                    /*
                     * Manhattan 距离 <= 1：
                     * 已经站到原木旁边，就不用再写 WALK_TARGET。
                     */
                    if (targetPos.distManhattan(maid.blockPosition()) <= CLOSE_ENOUGH_DISTANCE) {
                        walkTarget.erase();
                        cantReachWalkTargetSince.erase();
                        return true;
                    }
                    
                    if (!maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                        walkTarget.set(new WalkTarget(tracker, speedModifier, CLOSE_ENOUGH_DISTANCE));
                    }
                    
                    return true;
                }
            )

        );
    }

}

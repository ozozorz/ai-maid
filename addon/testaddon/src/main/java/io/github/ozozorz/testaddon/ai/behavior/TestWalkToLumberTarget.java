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

    // 现在 WALK_TARGET 是真正可站立的工作位置，
    // 所以要求精确到达这个方块，而不是“距离原木一格”。
    private static final int CLOSE_ENOUGH_DISTANCE = 0;

    // 初始路径虽然可达，但世界途中可能变化
    private static final long MAX_CANT_REACH_TICKS = 200L;

    private TestWalkToLumberTarget() {
    }

    public static BehaviorControl<AiMaidEntity> create(float speedModifier) {
        return BehaviorBuilder.create(i ->
            i.group(
                // 真正准备破坏的原木
                i.present(TestAddonMemoryModuleTypes.LUMBER_TARGET),
                // Maid 准备站立的位置
                i.present(TestAddonMemoryModuleTypes.LUMBER_WORK_POS),
                i.absent(TestAddonMemoryModuleTypes.LUMBER_DONE),
                i.registered(MemoryModuleType.WALK_TARGET),
                i.registered(MemoryModuleType.LOOK_TARGET),
                i.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
            ).apply(i, (lumberTarget, lumberWorkPos, lumberDone, walkTarget, lookTarget, cantReachWalkTargetSince) ->
                (level, maid, timestamp) -> {

                    BlockPos targetPos = i.get(lumberTarget);
                    BlockPos workPos = i.get(lumberWorkPos);

                    // 每 tick 都重新验证世界现实。
                    // 同时检查两个位置仍然是水平相邻关系。
                    if (!level.getGameRules().get(GameRules.MOB_GRIEFING) || !level.getBlockState(targetPos).is(BlockTags.LOGS) || targetPos.distManhattan(workPos) != 1) {
                        lumberDone.set(Unit.INSTANCE);
                        return true;
                    }
                    
                    // 观察目标仍然是原木，不是 Maid 自己准备站的位置。
                    lookTarget.set(new BlockPosTracker(targetPos));
                    
                    // 已经精确站到工作位置。
                    if (workPos.equals(maid.blockPosition())) {
                        walkTarget.erase();
                        cantReachWalkTargetSince.erase();
                        return true;
                    }

                    // 初次搜索时是可达的，但途中可能被玩家放置方块挡住。
                    // 所以仍然保留 Vanilla 的 CANT_REACH fallback。
                    Optional<Long> cantReachSince = maid.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                    if (cantReachSince.isPresent() && timestamp - cantReachSince.get() > MAX_CANT_REACH_TICKS) {
                        lumberDone.set(Unit.INSTANCE);
                        return true;
                    }

                    // 只负责发布导航意图。
                    // 真正创建并执行路径的仍然是 Core Activity 中的 MoveToTargetSink。
                    if (!maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                        walkTarget.set(new WalkTarget(workPos, speedModifier, CLOSE_ENOUGH_DISTANCE));
                    }
                    
                    return true;
                }
            )

        );
    }

}

package io.github.ozozorz.testaddon.ai.behavior;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.datafixers.util.Unit;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;

public class TestFindLumberTarget {

    // 限制一次交给寻路器的原木数量
    private static final int MAX_LOG_CANDIDATES = 32;


    private TestFindLumberTarget() {
    }

    public static BehaviorControl<AiMaidEntity> create(int horizontalRadius, int verticalRadius) {
        return BehaviorBuilder.create(instance ->
            instance.group(
                // 只有还没有目标时才搜索
                instance.absent(TestAddonMemoryModuleTypes.LUMBER_TARGET),
                // 还没选定Maid要站的位置
                instance.absent(TestAddonMemoryModuleTypes.LUMBER_WORK_POS),
                // 这轮工作还没结束
                instance.absent(TestAddonMemoryModuleTypes.LUMBER_DONE)
            ).apply(instance, (lumberTarget, lumberWorkPos, lumberDone) -> {
                return (level, maid, timestamp) -> {

                    // 和 Vanilla Villager 工作一致：
                    // Mob 修改世界首先服从 mobGriefing。
                    if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
                        lumberDone.set(Unit.INSTANCE);
                        return true;
                    }

                    Optional<TargetSelection> selection = findReachableTarget(level, maid, horizontalRadius, verticalRadius);

                    if (selection.isEmpty()) {
                        // 附近没有具有可达站位的原木
                        lumberDone.set(Unit.INSTANCE);
                        return true;
                    }

                    TargetSelection selected = selection.get();

                    // 保存“对什么工作”
                    lumberTarget.set(selected.logPos());

                    // 保存“站在哪里工作”
                    lumberWorkPos.set(selected.workPos());

                    return true;
                };
            })
        );
    }

    private static Optional<TargetSelection> findReachableTarget(ServerLevel level, AiMaidEntity maid, int horizontalRadius, int verticalRadius) {
        
        // key = 可供 maid 站立的位置
        // value = 从该位置准备破坏的原木
        Map<BlockPos, BlockPos> logByWorkPos = new LinkedHashMap<>();

        int candidateLogCount = 0;

        for (BlockPos cursor : BlockPos.withinManhattan(maid.blockPosition(), horizontalRadius, verticalRadius, horizontalRadius)) {
            
            if (!level.getBlockState(cursor).is(BlockTags.LOGS)) {
                continue;
            }

            // withinManhattan 会复用 MutableBlockPos，所以长期保存前必须转成 immutable。
            BlockPos logPos = cursor.immutable();

            boolean addedWorkPos = false;

            // 第一版只考虑与原木同高度的：北、东、南、西四个站位。
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos workPos = logPos.relative(direction);

                // 这是一个廉价的初步过滤：workPos 下方至少要有可视为实心支撑的方块。
                // 它不是完整的碰撞或可达性判断；最终判断仍交给 PathFinder。
                if (!maid.getNavigation().isStableDestination(workPos)) {
                    continue;
                }

                // 一个站位可能同时邻接两块原木。保留搜索顺序中先遇到的那一块。
                logByWorkPos.putIfAbsent(workPos, logPos);

                addedWorkPos = true;
            }

            if (addedWorkPos) {
                candidateLogCount++;
                
                if (candidateLogCount >= MAX_LOG_CANDIDATES) {
                    break;
                }
            }
        }

        if (logByWorkPos.isEmpty()) {
            return Optional.empty();
        }

        // 一次把所有候选站位交给 Vanilla PathFinder。
        // 这里只是在“询问能否到达”，并没有调用 navigation.moveTo()。
        Path path = maid.getNavigation().createPath(logByWorkPos.keySet(), 0);

        // path != null 仍可能只是部分路径。
        // canReach() 才表示真正抵达了某个候选站位。
        if (path == null || !path.canReach()) {
            return Optional.empty();
        }

        // 多目标寻路完成后，getTarget() 告诉我们最终选中了哪个站位。
        BlockPos selectedWorkPos = path.getTarget().immutable();
        BlockPos selectedLogPos = logByWorkPos.get(selectedWorkPos);

        if (selectedLogPos == null) {
            // 理论上不应该发生。这里防止未来导航实现变化后出现空映射。
            return Optional.empty();
        }

        return Optional.of(new TargetSelection(selectedLogPos, selectedWorkPos));
    }

    private record TargetSelection(BlockPos logPos, BlockPos workPos) {
    }

}

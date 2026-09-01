package io.github.ozozorz.testaddon.ai.behavior;

import com.mojang.datafixers.util.Unit;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Optional;
import java.util.logging.Level;

public class TestFindLumberTarget {

    private TestFindLumberTarget() {
    }

    public static BehaviorControl<AiMaidEntity> create(int horizontalRadius, int verticalRadius) {
        return BehaviorBuilder.create(instance ->
            instance.group(
                // 只有还没有目标时才搜索
                instance.absent(TestAddonMemoryModuleTypes.LUMBER_TARGET),
                // 这轮工作还没结束
                instance.absent(TestAddonMemoryModuleTypes.LUMBER_DONE)
            ).apply(instance, (lumberTarget, lumberDone) -> {
                return (Level, maid, timestamp) -> {

                    // 和 Vanilla Villager 工作一致：
                    // Mob 修改世界首先服从 mobGriefing。
                    if (!Level.getGameRules().get(GameRules.MOB_GRIEFING)) {
                        lumberDone.set(Unit.INSTANCE);
                        return true;
                    }

                    Optional<BlockPos> target = BlockPos.findClosestMatch(maid.blockPosition(), horizontalRadius, verticalRadius, pos -> Level.getBlockState(pos).is(BlockTags.LOGS));
                    // 这次附近没有原木，本轮尝试结束。
                    if (target.isEmpty()) {
                        lumberDone.set(Unit.INSTANCE);
                        return true;
                    }

                    /*
                     * 很重要：
                     * findClosestMatch 内部的遍历会复用 MutableBlockPos。
                     * 存进 Brain 前必须固定成 immutable BlockPos。
                     */
                    lumberTarget.set(target.get().immutable());

                    return true;
                };
            })
        );
    }

}

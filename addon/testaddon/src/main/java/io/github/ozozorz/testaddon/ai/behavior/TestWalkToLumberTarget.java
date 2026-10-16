package io.github.ozozorz.testaddon.ai.behavior;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidBlockBreaking;
import io.github.ozozorz.aimaid.entity.inventory.MaidEquipmentHelper;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.lumber.LumberJob;
import io.github.ozozorz.testaddon.lumber.LumberWorkPlanner;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.gamerules.GameRules;

public final class TestWalkToLumberTarget {
    private TestWalkToLumberTarget() {}
    public static BehaviorControl<AiMaidEntity> create(float speed) {
        return BehaviorBuilder.create(i -> i.group(
                i.present(TestAddonMemoryModuleTypes.LUMBER_TARGET),
                i.present(TestAddonMemoryModuleTypes.LUMBER_WORK_POS),
                i.present(TestAddonMemoryModuleTypes.LUMBER_JOB),
                i.registered(MemoryModuleType.WALK_TARGET),
                i.registered(MemoryModuleType.LOOK_TARGET),
                i.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
        ).apply(i, (target, work, jobMemory, walk, look, cantReach) -> (level, maid, now) -> {
            if (!LumberJob.isWorking(maid)) return false;
            if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) { LumberJob.finish(maid, "disabled"); return true; }
            if (!MaidEquipmentHelper.ensureEquipment(maid, EquipmentSlot.MAINHAND, stack -> stack.is(ItemTags.AXES))) {
                LumberJob.finish(maid, "no_tool"); return true;
            }
            var pos = i.get(target);
            var feet = i.get(work);
            var job = i.get(jobMemory);
            var expected = job.remaining().get(pos);
            if (job.activeTicks(now) > 6000) { LumberJob.finish(maid, "timeout"); return true; }
            boolean pathFailed = maid.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                    .map(since -> now - since > 200).orElse(false);
            if (expected == null || !level.hasChunkAt(pos) || level.getBlockState(pos) != expected
                    || !MaidBlockBreaking.canBreak(level, maid, pos, expected, maid.getMainHandItem())
                    || !LumberWorkPlanner.canStand(level, maid, feet) || pathFailed
                    || !maid.blockPosition().equals(feet) && now - job.targetSince > 240) {
                job.complete(pos, false);
                LumberJob.clearTarget(maid);
                return true;
            }
            look.set(new BlockPosTracker(pos));
            if (maid.blockPosition().equals(feet) && MaidBlockBreaking.canReach(level, maid, pos)) {
                walk.erase(); cantReach.erase();
            } else if (maid.blockPosition().equals(feet)) {
                // 途中新增遮挡，放弃当前目标，下一次寻找其余原木。
                job.complete(pos, false); LumberJob.clearTarget(maid);
            } else if (!maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                walk.set(new WalkTarget(feet, speed, 0));
            }
            return true;
        }));
    }
}

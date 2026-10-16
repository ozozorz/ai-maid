package io.github.ozozorz.testaddon.ai.behavior;

import com.mojang.datafixers.util.Unit;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.inventory.MaidEquipmentHelper;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.lumber.LumberJob;
import io.github.ozozorz.testaddon.lumber.LumberTreeFinder;
import io.github.ozozorz.testaddon.lumber.LumberWorkPlanner;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.level.gamerules.GameRules;

public final class TestFindLumberTarget {
    private TestFindLumberTarget() {}
    public static BehaviorControl<AiMaidEntity> create() {
        return BehaviorBuilder.create(i -> i.group(
                i.absent(TestAddonMemoryModuleTypes.LUMBER_TARGET),
                i.absent(TestAddonMemoryModuleTypes.LUMBER_WORK_POS),
                i.absent(TestAddonMemoryModuleTypes.LUMBER_DONE),
                i.registered(TestAddonMemoryModuleTypes.LUMBER_JOB),
                i.registered(TestAddonMemoryModuleTypes.LUMBER_REQUEST)
        ).apply(i, (target, work, done, jobMemory, request) -> (level, maid, now) -> {
            if (!LumberJob.isWorking(maid)) return false;
            var job = maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_JOB).orElse(null);
            if (job == null && !maid.getBrain().hasMemoryValue(TestAddonMemoryModuleTypes.LUMBER_REQUEST)) {
                done.set(Unit.INSTANCE); return true;
            }
            if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
                LumberJob.finish(maid, "disabled"); return true;
            }
            if (!MaidEquipmentHelper.ensureEquipment(maid, EquipmentSlot.MAINHAND, stack -> stack.is(ItemTags.AXES))) {
                LumberJob.finish(maid, "no_tool"); return true;
            }
            if (job == null) {
                var requested = maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_REQUEST);
                if (requested.isEmpty()) {
                    // 重载只恢复命令，不把旧的一次请求变成新一轮自动砍树。
                    done.set(Unit.INSTANCE); return true;
                }
                request.erase();
                var tree = LumberTreeFinder.find(level, maid.blockPosition(), requested.get());
                if (tree.isEmpty()) { LumberJob.finish(maid, "no_tree"); return true; }
                job = new LumberJob(tree.get(), now);
                jobMemory.set(job);
            }
            if (now - job.startedAt > 6000) { LumberJob.finish(maid, "timeout"); return true; }
            if (job.remaining().isEmpty()) { LumberJob.finish(maid, "complete"); return true; }
            // 刚生成/下落时 GroundPathNavigation 尚不能寻路，null 不代表目标不可达。
            if (!maid.onGround()) return false;
            var selection = LumberWorkPlanner.choose(level, maid, job);
            if (selection.isPresent()) {
                target.set(selection.get().log());
                work.set(selection.get().feet());
                job.targetSince = now;
            } else if (job.remaining().isEmpty()) {
                LumberJob.finish(maid, "complete");
            }
            return true;
        }));
    }
}

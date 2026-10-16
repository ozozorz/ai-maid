package io.github.ozozorz.testaddon.combat;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.command.TestLumberCommand;
import io.github.ozozorz.testaddon.command.TestPatrolCommand;
import io.github.ozozorz.testaddon.lumber.LumberJob;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/** 现有两种工作的中断规则；任务仍放在各自的 Memory 中，不复制成另一份任务。 */
public final class WorkDefense {
    private WorkDefense() {}

    public static boolean supports(MaidCommand command) {
        return command instanceof TestLumberCommand || command instanceof TestPatrolCommand;
    }

    public static void suspend(AiMaidEntity maid, long now) {
        if (maid.getMaidCommand() instanceof TestLumberCommand) {
            maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_JOB).ifPresent(job -> job.pause(now));
            // 原木还在剩余队列里。战后重新规划脚部站位，不沿用战前的导航和破坏进度。
            LumberJob.clearTarget(maid);
        }
        // 巡逻保留原目标、中心和半径；停留计时可以自然到期。
    }

    public static void resume(AiMaidEntity maid, long now) {
        // 战斗也会从副手取武器，原斧头可能因此被换到副手。
        // 只使用此刻仍真实存在的栈；storage 的工具由原工作行为保证。
        if (supports(maid.getMaidCommand()) && !maid.getMainHandItem().is(ItemTags.AXES)) {
            CombatEquipment.swapOffhand(maid, stack -> stack.is(ItemTags.AXES));
        }
        if (maid.getMaidCommand() instanceof TestLumberCommand) {
            maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_JOB).ifPresent(job -> job.resume(now));
            LumberJob.clearTarget(maid);
        }
    }

    /** 显式换命令与战斗结束共用的临时意图清理，不删除工作任务。 */
    public static void clearCombat(AiMaidEntity maid) {
        var brain = maid.getBrain();
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        maid.stopUsingItem();
        maid.setAggressive(false);
    }
}

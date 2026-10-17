package io.github.ozozorz.aimaid.entity.combat;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/** Core 只通知暂停/恢复，不了解 Addon 的作物、队列或货物。 */
public final class WorkDefense {
    private WorkDefense() {}
    public static boolean supports(MaidCommand command) {
        return command != null && command.getCombatMode() == MaidCombatMode.SELF_DEFENSE;
    }
    public static void suspend(AiMaidEntity maid, long now) { maid.getMaidCommand().onCombatStarted(maid, now); }
    public static void resume(AiMaidEntity maid, long now) { maid.getMaidCommand().onCombatEnded(maid, now); }
    public static void clearCombat(AiMaidEntity maid) {
        var brain = maid.getBrain();
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        maid.getNavigation().stop();
        maid.stopUsingItem();
        maid.setAggressive(false);
    }
}

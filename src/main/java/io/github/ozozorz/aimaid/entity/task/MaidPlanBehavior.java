package io.github.ozozorz.aimaid.entity.task;

import java.util.Map;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public final class MaidPlanBehavior extends Behavior<AiMaidEntity> {
    public MaidPlanBehavior() {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT));
    }
    private boolean active(AiMaidEntity maid) {
        return maid.getMaidCommand() == MaidCommands.PLAN && MaidTasks.current(maid) instanceof MaidTaskRunner task
                && task.active() && !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }
    @Override protected boolean timedOut(long now) { return false; }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, AiMaidEntity maid) { return active(maid); }
    @Override protected boolean canStillUse(ServerLevel level, AiMaidEntity maid, long now) { return active(maid); }
    @Override protected void tick(ServerLevel level, AiMaidEntity maid, long now) {
        if (MaidTasks.current(maid) instanceof MaidTaskRunner task) task.tick(maid, now);
    }
    @Override protected void stop(ServerLevel level, AiMaidEntity maid, long now) {
        if (MaidTasks.current(maid) instanceof MaidTaskRunner task) task.clearInteraction(maid);
    }
}

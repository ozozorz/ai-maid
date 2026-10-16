package io.github.ozozorz.testaddon.lumber;

import java.util.LinkedHashMap;
import java.util.Map;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.command.TestLumberCommand;
import com.mojang.datafixers.util.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.state.BlockState;

/** 每只 Brain 的一次有限任务；不放进共享 Command，也不持久化旧进度。 */
public final class LumberJob {
    private final Map<BlockPos, BlockState> remaining;
    public final long startedAt;
    public long targetSince;
    public int broken;
    public int skipped;

    public LumberJob(Map<BlockPos, BlockState> blocks, long now) {
        remaining = new LinkedHashMap<>(blocks);
        startedAt = now;
    }

    public Map<BlockPos, BlockState> remaining() { return remaining; }
    public void complete(BlockPos pos, boolean success) {
        if (remaining.remove(pos) != null) {
            if (success) broken++; else skipped++;
        }
    }

    public static boolean isWorking(AiMaidEntity maid) {
        return maid.isAlive() && maid.isTame() && maid.getMaidCommand() instanceof TestLumberCommand
                && !maid.getBrain().hasMemoryValue(TestAddonMemoryModuleTypes.LUMBER_DONE);
    }

    public static void clearTarget(AiMaidEntity maid) {
        var brain = maid.getBrain();
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_TARGET);
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_WORK_POS);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    public static void finish(AiMaidEntity maid, String reason) {
        if (maid.getBrain().hasMemoryValue(TestAddonMemoryModuleTypes.LUMBER_DONE)) return;
        var job = maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_JOB).orElse(null);
        int broken = job == null ? 0 : job.broken;
        int skipped = job == null ? 0 : job.skipped + job.remaining.size();
        clearTarget(maid);
        maid.getBrain().setMemory(TestAddonMemoryModuleTypes.LUMBER_DONE, Unit.INSTANCE);
        if (maid.getOwner() instanceof ServerPlayer owner) {
            owner.sendSystemMessage(Component.translatable("message.testaddon.lumber.finished", maid.getDisplayName(),
                    broken, skipped, Component.translatable("message.testaddon.lumber." + reason)));
        }
    }
}

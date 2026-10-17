package io.github.ozozorz.aimaid.lumber.lumber;

import java.util.LinkedHashMap;
import java.util.Map;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.lumber.ai.LumberMemories;
import io.github.ozozorz.aimaid.lumber.command.LumberCommand;
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
    private long pausedAt = -1;
    private long pausedTicks;

    public LumberJob(Map<BlockPos, BlockState> blocks, long now) {
        remaining = new LinkedHashMap<>(blocks);
        startedAt = now;
    }

    public Map<BlockPos, BlockState> remaining() { return remaining; }

    public void pause(long now) {
        if (pausedAt < 0) pausedAt = now;
    }

    public void resume(long now) {
        if (pausedAt >= 0) {
            pausedTicks += now - pausedAt;
            pausedAt = -1;
        }
    }

    public long activeTicks(long now) {
        return (pausedAt < 0 ? now : pausedAt) - startedAt - pausedTicks;
    }
    public void complete(BlockPos pos, boolean success) {
        if (remaining.remove(pos) != null) {
            if (success) broken++; else skipped++;
        }
    }

    public static boolean isWorking(AiMaidEntity maid) {
        return maid.isAlive() && maid.isTame() && maid.getMaidCommand() instanceof LumberCommand
                && !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !maid.getBrain().hasMemoryValue(LumberMemories.LUMBER_DONE);
    }

    public static void clearTarget(AiMaidEntity maid) {
        var brain = maid.getBrain();
        brain.eraseMemory(LumberMemories.LUMBER_TARGET);
        brain.eraseMemory(LumberMemories.LUMBER_WORK_POS);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    public static void finish(AiMaidEntity maid, String reason) {
        if (maid.getBrain().hasMemoryValue(LumberMemories.LUMBER_DONE)) return;
        var job = maid.getBrain().getMemory(LumberMemories.LUMBER_JOB).orElse(null);
        int broken = job == null ? 0 : job.broken;
        int skipped = job == null ? 0 : job.skipped + job.remaining.size();
        clearTarget(maid);
        maid.getBrain().eraseMemory(LumberMemories.LUMBER_JOB);
        maid.getBrain().eraseMemory(LumberMemories.LUMBER_REQUEST);
        maid.getBrain().setMemory(LumberMemories.LUMBER_DONE, Unit.INSTANCE);
        maid.getBrain().setMemory(LumberMemories.LUMBER_RESULT,
                Component.translatable("status.ai-maid-lumber.lumber." + reason, broken, skipped));
        if (maid.getOwner() instanceof ServerPlayer owner) {
            owner.sendSystemMessage(Component.translatable("message.ai-maid-lumber.lumber.finished", maid.getDisplayName(),
                    broken, skipped, Component.translatable("message.ai-maid-lumber.lumber." + reason)));
        }
    }
}

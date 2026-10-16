package io.github.ozozorz.testaddon.ai.behavior;

import java.util.Map;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidBlockBreaking;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.lumber.LumberJob;
import io.github.ozozorz.testaddon.animation.TestAddonAnimationIds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** 一个运行中的 Behavior 保存本只女仆的一次破坏进度；停止必清除裂纹。 */
public class TestBreakLumberTarget extends Behavior<AiMaidEntity> {
    private BlockPos breaking;
    private BlockState state;
    private ItemStack tool = ItemStack.EMPTY;
    private ItemStack toolSnapshot = ItemStack.EMPTY;
    private float progress;
    private int lastStage = -1;
    private long startedAt;
    private int animationToken;

    public TestBreakLumberTarget() {
        super(Map.of(TestAddonMemoryModuleTypes.LUMBER_TARGET, MemoryStatus.VALUE_PRESENT,
                TestAddonMemoryModuleTypes.LUMBER_WORK_POS, MemoryStatus.VALUE_PRESENT,
                TestAddonMemoryModuleTypes.LUMBER_JOB, MemoryStatus.VALUE_PRESENT,
                TestAddonMemoryModuleTypes.LUMBER_DONE, MemoryStatus.VALUE_ABSENT));
    }

    @Override protected boolean timedOut(long now) { return false; }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, AiMaidEntity maid) {
        var pos = maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_TARGET).orElse(null);
        var feet = maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_WORK_POS).orElse(null);
        return LumberJob.isWorking(maid) && maid.onGround() && maid.blockPosition().equals(feet)
                && maid.getMainHandItem().is(ItemTags.AXES) && pos != null
                && MaidBlockBreaking.canReach(level, maid, pos);
    }

    @Override
    protected void start(ServerLevel level, AiMaidEntity maid, long now) {
        breaking = maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_TARGET).orElseThrow();
        state = level.getBlockState(breaking);
        resetTool(maid, now);
    }

    private void resetTool(AiMaidEntity maid, long now) {
        tool = maid.getMainHandItem();
        toolSnapshot = tool.copy();
        progress = 0;
        startedAt = now;
        animationToken = maid.playAnimationAction(TestAddonAnimationIds.LUMBER, 1200);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, AiMaidEntity maid, long now) {
        return breaking != null && checkExtraStartConditions(level, maid)
                && maid.getBrain().isMemoryValue(TestAddonMemoryModuleTypes.LUMBER_TARGET, breaking)
                && level.getBlockState(breaking) == state;
    }

    @Override
    protected void tick(ServerLevel level, AiMaidEntity maid, long now) {
        if (tool != maid.getMainHandItem() || !ItemStack.isSameItemSameComponents(tool, toolSnapshot)) {
            level.destroyBlockProgress(maid.getId(), breaking, -1);
            lastStage = -1;
            resetTool(maid, now);
        }
        var job = maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_JOB).orElseThrow();
        if (!MaidBlockBreaking.canBreak(level, maid, breaking, state, tool) || now - startedAt > 1200) {
            job.complete(breaking, false);
            LumberJob.clearTarget(maid);
            doStop(level, maid, now);
            return;
        }
        if ((now - startedAt) % 6 == 0) maid.swing(InteractionHand.MAIN_HAND);
        progress += MaidBlockBreaking.progressPerTick(level, maid, breaking, state, tool);
        if (progress >= 1) {
            boolean destroyed = MaidBlockBreaking.destroy(level, maid, breaking, state, tool);
            job.complete(breaking, destroyed);
            LumberJob.clearTarget(maid);
            doStop(level, maid, now);
            return;
        }
        int stage = Math.min(9, (int) (progress * 10));
        if (stage != lastStage) {
            level.destroyBlockProgress(maid.getId(), breaking, stage);
            lastStage = stage;
        }
    }

    @Override
    protected void stop(ServerLevel level, AiMaidEntity maid, long now) {
        maid.stopAnimationAction(TestAddonAnimationIds.LUMBER, animationToken);
        if (breaking != null) level.destroyBlockProgress(maid.getId(), breaking, -1);
        breaking = null;
        tool = toolSnapshot = ItemStack.EMPTY;
        progress = 0;
        lastStage = -1;
    }
}

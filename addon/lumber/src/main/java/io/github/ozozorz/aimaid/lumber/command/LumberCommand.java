package io.github.ozozorz.aimaid.lumber.command;

import net.minecraft.network.chat.Component;
import io.github.ozozorz.aimaid.entity.inventory.MaidEquipmentRules;
import io.github.ozozorz.aimaid.entity.combat.CombatStatus;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.tags.ItemTags;

import java.util.List;
import java.util.Set;
import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import io.github.ozozorz.aimaid.command.MaidCommandCommandApi;
import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetResolver;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import io.github.ozozorz.aimaid.lumber.ai.LumberActivities;
import io.github.ozozorz.aimaid.lumber.ai.LumberMemories;
import io.github.ozozorz.aimaid.lumber.ai.behavior.BreakLumberTarget;
import io.github.ozozorz.aimaid.lumber.ai.behavior.FindLumberTarget;
import io.github.ozozorz.aimaid.lumber.ai.behavior.WalkToLumberTarget;
import io.github.ozozorz.aimaid.lumber.lumber.LumberJob;
import io.github.ozozorz.aimaid.entity.combat.WorkDefense;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

public class LumberCommand implements MaidCommand {
    @Override public io.github.ozozorz.aimaid.entity.combat.MaidCombatMode getCombatMode() { return io.github.ozozorz.aimaid.entity.combat.MaidCombatMode.SELF_DEFENSE; }
    @Override public void onCombatStarted(AiMaidEntity maid, long now) {
        maid.getBrain().getMemory(LumberMemories.LUMBER_JOB).ifPresent(job -> job.pause(now));
        LumberJob.clearTarget(maid);
    }
    @Override public void onCombatEnded(AiMaidEntity maid, long now) {
        if (!maid.getMainHandItem().is(ItemTags.AXES) && maid.getOffhandItem().is(ItemTags.AXES))
            io.github.ozozorz.aimaid.entity.inventory.MaidItemTransfer.swapHands(maid);
        maid.getBrain().getMemory(LumberMemories.LUMBER_JOB).ifPresent(job -> job.resume(now));
        LumberJob.clearTarget(maid);
    }
    @Override
    public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of(ActivityData.create(LumberActivities.LUMBER,
                ActivityData.createPriorityPairs(10, ImmutableList.of(FindLumberTarget.create(),
                        WalkToLumberTarget.create(0.7F), new BreakLumberTarget())),
                Set.of(Pair.of(LumberMemories.LUMBER_DONE, MemoryStatus.VALUE_ABSENT),
                        Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)),
                Set.of(LumberMemories.LUMBER_TARGET, LumberMemories.LUMBER_WORK_POS,
                        MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET,
                        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)));
    }

    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return List.of(ModActivities.FIGHT, LumberActivities.LUMBER,
                ModActivities.PICK_UP_ITEM, Activity.IDLE);
    }

    @Override public int getMenuOrder() { return 500; }

    @Override
    public void onSelected(AiMaidEntity maid) {
        LumberJob.clearTarget(maid);
        var brain = maid.getBrain();
        brain.eraseMemory(LumberMemories.LUMBER_DONE);
        brain.eraseMemory(LumberMemories.LUMBER_RESULT);
        brain.eraseMemory(LumberMemories.LUMBER_JOB);
        brain.setMemory(LumberMemories.LUMBER_REQUEST, false);
    }

    @Override
    public void onDeselected(AiMaidEntity maid) {
        WorkDefense.clearCombat(maid);
        LumberJob.clearTarget(maid);
        var brain = maid.getBrain();
        brain.eraseMemory(LumberMemories.LUMBER_JOB);
        brain.eraseMemory(LumberMemories.LUMBER_REQUEST);
        brain.eraseMemory(LumberMemories.LUMBER_DONE);
        brain.eraseMemory(LumberMemories.LUMBER_RESULT);
    }

    @Override public Component getStatus(AiMaidEntity maid) {
        if (maid.getTarget() != null) return CombatStatus.describe(maid);
        var result = maid.getBrain().getMemory(LumberMemories.LUMBER_RESULT);
        if (result.isPresent()) return result.get();
        if (maid.getBrain().hasMemoryValue(LumberMemories.LUMBER_DONE))
            return Component.translatable("status.ai-maid-lumber.lumber.reselect");
        if (!MaidEquipmentRules.canRemove(maid, EquipmentSlot.MAINHAND, null)
                && !maid.getMainHandItem().is(ItemTags.AXES))
            return Component.translatable("status.ai-maid-lumber.equipment_locked");
        return Component.empty();
    }

    @Override
    public void buildServerCommand(LiteralArgumentBuilder<CommandSourceStack> node, CommandBuildContext context,
            MaidTargetResolver resolver) {
        node.executes(command -> MaidCommandCommandApi.executeSelection(command, resolver, this));
        // 明确的单块模式保留用于独立作业与验收；GUI 默认识别一棵树。
        node.then(Commands.literal("single").executes(command -> {
            AiMaidEntity maid = MaidCommandCommandApi.resolveSelectableTarget(command, resolver, this);
            int result = MaidCommandCommandApi.finishSelection(command, maid, this);
            if (result > 0) maid.getBrain().setMemory(LumberMemories.LUMBER_REQUEST, true);
            return result;
        }));
    }

    @Override public boolean allowsItemPickup(AiMaidEntity maid, ServerLevel level, ItemStack stack) {
        return !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }
}

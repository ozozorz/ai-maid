package io.github.ozozorz.testaddon.command;

import net.minecraft.network.chat.Component;
import io.github.ozozorz.aimaid.entity.inventory.MaidEquipmentRules;
import io.github.ozozorz.testaddon.combat.CombatStatus;
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
import io.github.ozozorz.testaddon.ai.TestAddonActivities;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.ai.behavior.TestBreakLumberTarget;
import io.github.ozozorz.testaddon.ai.behavior.TestFindLumberTarget;
import io.github.ozozorz.testaddon.ai.behavior.TestWalkToLumberTarget;
import io.github.ozozorz.testaddon.lumber.LumberJob;
import io.github.ozozorz.testaddon.combat.WorkDefense;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

public class TestLumberCommand implements MaidCommand {
    @Override
    public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of(ActivityData.create(TestAddonActivities.LUMBER,
                ActivityData.createPriorityPairs(10, ImmutableList.of(TestFindLumberTarget.create(),
                        TestWalkToLumberTarget.create(0.7F), new TestBreakLumberTarget())),
                Set.of(Pair.of(TestAddonMemoryModuleTypes.LUMBER_DONE, MemoryStatus.VALUE_ABSENT),
                        Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)),
                Set.of(TestAddonMemoryModuleTypes.LUMBER_TARGET, TestAddonMemoryModuleTypes.LUMBER_WORK_POS,
                        MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET,
                        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)));
    }

    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return List.of(TestAddonActivities.GUARD_FIGHT, TestAddonActivities.LUMBER,
                ModActivities.PICK_UP_ITEM, Activity.IDLE);
    }

    @Override public int getMenuOrder() { return 500; }

    @Override
    public void onSelected(AiMaidEntity maid) {
        LumberJob.clearTarget(maid);
        var brain = maid.getBrain();
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_DONE);
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_RESULT);
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_JOB);
        brain.setMemory(TestAddonMemoryModuleTypes.LUMBER_REQUEST, false);
    }

    @Override
    public void onDeselected(AiMaidEntity maid) {
        WorkDefense.clearCombat(maid);
        LumberJob.clearTarget(maid);
        var brain = maid.getBrain();
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_JOB);
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_REQUEST);
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_DONE);
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_RESULT);
    }

    @Override public Component getStatus(AiMaidEntity maid) {
        if (maid.getTarget() != null) return CombatStatus.describe(maid);
        var result = maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_RESULT);
        if (result.isPresent()) return result.get();
        if (maid.getBrain().hasMemoryValue(TestAddonMemoryModuleTypes.LUMBER_DONE))
            return Component.translatable("status.testaddon.lumber.reselect");
        if (!MaidEquipmentRules.canRemove(maid, EquipmentSlot.MAINHAND, null)
                && !maid.getMainHandItem().is(ItemTags.AXES))
            return Component.translatable("status.testaddon.equipment_locked");
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
            if (result > 0) maid.getBrain().setMemory(TestAddonMemoryModuleTypes.LUMBER_REQUEST, true);
            return result;
        }));
    }

    @Override public boolean allowsItemPickup(AiMaidEntity maid, ServerLevel level, ItemStack stack) {
        return !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }
}

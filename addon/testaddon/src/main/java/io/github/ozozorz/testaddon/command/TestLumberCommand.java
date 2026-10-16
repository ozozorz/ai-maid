package io.github.ozozorz.testaddon.command;

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
                Set.of(Pair.of(TestAddonMemoryModuleTypes.LUMBER_DONE, MemoryStatus.VALUE_ABSENT)),
                Set.of(TestAddonMemoryModuleTypes.LUMBER_TARGET, TestAddonMemoryModuleTypes.LUMBER_WORK_POS,
                        TestAddonMemoryModuleTypes.LUMBER_JOB, TestAddonMemoryModuleTypes.LUMBER_REQUEST,
                        MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET,
                        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)));
    }

    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return List.of(TestAddonActivities.LUMBER, ModActivities.PICK_UP_ITEM, Activity.IDLE);
    }

    @Override public int getMenuOrder() { return 500; }

    @Override
    public void onSelected(AiMaidEntity maid) {
        LumberJob.clearTarget(maid);
        var brain = maid.getBrain();
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_DONE);
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_JOB);
        brain.setMemory(TestAddonMemoryModuleTypes.LUMBER_REQUEST, false);
    }

    @Override
    public void buildServerCommand(LiteralArgumentBuilder<CommandSourceStack> node, CommandBuildContext context,
            MaidTargetResolver resolver) {
        node.executes(command -> MaidCommandCommandApi.executeSelection(command, resolver, this));
        // 明确的单块模式保留用于独立作业与验收；GUI 默认识别一棵树。
        node.then(Commands.literal("single").executes(command -> {
            AiMaidEntity maid = MaidCommandCommandApi.resolveSelectableTarget(command, resolver, this);
            int result = MaidCommandCommandApi.finishSelection(command, maid, this);
            maid.getBrain().setMemory(TestAddonMemoryModuleTypes.LUMBER_REQUEST, true);
            return result;
        }));
    }

    @Override public boolean allowsItemPickup(AiMaidEntity maid, ServerLevel level, ItemStack stack) { return true; }
}

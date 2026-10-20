package io.github.ozozorz.aimaid.entity.maidcommand;

import java.util.*;
import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import io.github.ozozorz.aimaid.command.MaidCommandCommandApi;
import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetResolver;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.combat.*;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import io.github.ozozorz.aimaid.entity.task.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.entity.schedule.Activity;

public final class PlanMaidCommand implements MaidCommand {
    @Override public boolean isVisibleInCommandMenu() { return false; }
    @Override public MaidCombatMode getCombatMode() { return MaidCombatMode.SELF_DEFENSE; }
    @Override public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of(ActivityData.create(ModActivities.PLAN,
                ActivityData.createPriorityPairs(10, ImmutableList.of(new MaidPlanBehavior())),
                Set.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)),
                Set.of(MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)));
    }
    @Override public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return MaidTasks.current(maid) instanceof MaidTaskRunner task && task.active()
                ? List.of(ModActivities.FIGHT, ModActivities.PLAN, ModActivities.STAY) : List.of(ModActivities.FIGHT, ModActivities.STAY);
    }
    @Override public void onDeselected(AiMaidEntity maid) { MaidTasks.cancel(maid, "manual_override"); WorkDefense.clearCombat(maid); }
    @Override public void onCombatStarted(AiMaidEntity maid, long now) { if (MaidTasks.current(maid) instanceof MaidTaskRunner task) task.pause(maid); }
    @Override public void onCombatEnded(AiMaidEntity maid, long now) { if (MaidTasks.current(maid) instanceof MaidTaskRunner task) task.resume(maid); }
    @Override public Component getStatus(AiMaidEntity maid) {
        return MaidTasks.current(maid) instanceof MaidTaskRunner task ? task.status(maid) : Component.translatable("status.ai-maid.plan.not_running");
    }
    @Override public void buildServerCommand(LiteralArgumentBuilder<CommandSourceStack> node, CommandBuildContext build, MaidTargetResolver resolver) {
        node.then(Commands.literal("status").executes(c -> {
            var maid = MaidCommandCommandApi.resolveSelectableTarget(c, resolver, this);
            c.getSource().sendSuccess(() -> getStatus(maid), false); return 1;
        }));
        node.then(Commands.literal("cancel").executes(c -> {
            var maid = MaidCommandCommandApi.resolveSelectableTarget(c, resolver, this);
            if (maid.isAlive() && maid.getMaidCommand() == this) {
                MaidTasks.cancel(maid, "cancelled"); maid.selectMaidCommand(MaidCommands.STAY);
            }
            c.getSource().sendSuccess(() -> getStatus(maid), false); return 1;
        }));
    }
}

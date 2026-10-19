package io.github.ozozorz.aimaid.farming;

import java.util.List;
import java.util.Set;
import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import io.github.ozozorz.aimaid.command.MaidCommandCommandApi;
import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetResolver;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.combat.*;
import io.github.ozozorz.aimaid.entity.inventory.MaidContainers;
import io.github.ozozorz.aimaid.entity.maidcommand.*;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import io.github.ozozorz.aimaid.entity.task.MaidTasks;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;

public final class WheatDeliveryCommand implements MaidCommand {
    // This job needs parameters, so a parameterless menu selection must not start it.
    @Override public boolean isVisibleInCommandMenu() { return false; }
    @Override public MaidCombatMode getCombatMode() { return MaidCombatMode.SELF_DEFENSE; }
    @Override public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of(ActivityData.create(FarmingAddon.DELIVERY_ACTIVITY,
                ActivityData.createPriorityPairs(10, ImmutableList.of(new WheatDeliveryBehavior())),
                Set.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)),
                Set.of(MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)));
    }
    @Override public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return MaidTasks.current(maid) instanceof WheatDeliveryTask task && task.active()
                ? List.of(ModActivities.FIGHT, FarmingAddon.DELIVERY_ACTIVITY, ModActivities.STAY)
                : List.of(ModActivities.FIGHT, ModActivities.STAY);
    }
    @Override public void onDeselected(AiMaidEntity maid) { MaidTasks.cancel(maid, "manual_override"); WorkDefense.clearCombat(maid); }
    @Override public void onCombatStarted(AiMaidEntity maid, long now) {
        if (MaidTasks.current(maid) instanceof WheatDeliveryTask task) task.pause(maid);
    }
    @Override public void onCombatEnded(AiMaidEntity maid, long now) {
        if (MaidTasks.current(maid) instanceof WheatDeliveryTask task) task.resume(maid);
    }
    @Override public Component getStatus(AiMaidEntity maid) {
        return MaidTasks.current(maid) instanceof WheatDeliveryTask task ? task.status(maid)
                : Component.translatable("status.ai-maid-farming.delivery.not_running");
    }

    @Override public void buildServerCommand(LiteralArgumentBuilder<CommandSourceStack> node,
            CommandBuildContext build, MaidTargetResolver resolver) {
        node.then(Commands.argument("count", IntegerArgumentType.integer(1, WheatDeliveryTask.MAX_COUNT))
                .executes(c -> start(c, resolver, MaidContainers.lookedAtChest(c.getSource().getPlayerOrException())))
                .then(Commands.argument("chest", BlockPosArgument.blockPos())
                        .executes(c -> start(c, resolver, BlockPosArgument.getLoadedBlockPos(c, "chest")))));
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

    private int start(CommandContext<CommandSourceStack> c, MaidTargetResolver resolver, BlockPos chest) throws CommandSyntaxException {
        var maid = MaidCommandCommandApi.resolveSelectableTarget(c, resolver, this);
        var owner = c.getSource().getPlayerOrException();
        int count = IntegerArgumentType.getInteger(c, "count");
        String error = null;
        if (!owner.isAlive() || owner.isSpectator() || owner.level() != maid.level() || maid.distanceToSqr(owner) > 32 * 32)
            error = "owner_unavailable";
        else if (chest == null) error = "look_at_chest";
        else if (chest.distToCenterSqr(maid.position()) > WheatDeliveryTask.DESTINATION_RANGE * WheatDeliveryTask.DESTINATION_RANGE)
            error = "chest_too_far";
        else if (MaidContainers.resolveChest(owner.level(), chest) == null) error = "chest_unavailable";
        if (error != null) {
            c.getSource().sendFailure(Component.translatable("status.ai-maid-farming.delivery.reason." + error)); return 0;
        }
        if (MaidTasks.current(maid) instanceof WheatDeliveryTask current && current.active()) {
            if (current.sameRequest(count, chest)) {
                c.getSource().sendSuccess(() -> current.status(maid), false); return 1;
            }
            c.getSource().sendFailure(Component.translatable("status.ai-maid-farming.delivery.reason.task_busy")); return 0;
        }
        if (MaidCommandCommandApi.finishSelection(c, maid, this) <= 0) return 0;
        MaidTasks.install(maid, new WheatDeliveryTask(maid, count, chest));
        io.github.ozozorz.aimaid.entity.ai.AiMaidAi.updateActivity(maid);
        c.getSource().sendSuccess(() -> Component.translatable("message.ai-maid-farming.delivery.started", count, chest.toShortString()), false);
        return 1;
    }
}

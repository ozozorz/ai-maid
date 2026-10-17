package io.github.ozozorz.aimaid.hauling;
import java.util.List;
import java.util.Set;
import com.mojang.datafixers.util.Pair;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import io.github.ozozorz.aimaid.command.MaidCommandCommandApi;
import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetResolver;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import io.github.ozozorz.aimaid.entity.combat.*;

public final class HaulingCommand implements MaidCommand {
    @Override public MaidCombatMode getCombatMode() { return MaidCombatMode.SELF_DEFENSE; }
    @Override public int getMenuOrder() { return 800; }
    @Override public boolean canUseInventoryItem(AiMaidEntity maid, int slot) {
        var cargo = maid.getAttached(HaulingCargo.TYPE);
        return cargo == null || cargo.slot() != slot || !cargo.matches(maid);
    }
    @Override public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        var work = new HaulingBehavior();
        var idle = new io.github.ozozorz.aimaid.entity.ai.behavior.MaidWorkIdle(
                m -> work.isWaiting(), m -> m.getAttached(HaulingData.CENTER), HaulingData::radius);
        return List.of(ActivityData.create(HaulingAddon.ACTIVITY,
                ActivityData.createPriorityPairs(10, com.google.common.collect.ImmutableList.of(work, idle)),
                Set.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)),
                Set.of(MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)));
    }
    @Override public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return List.of(ModActivities.FIGHT, HaulingAddon.ACTIVITY, Activity.IDLE);
    }
    @Override public void onSelected(AiMaidEntity maid) {
        maid.setAttached(HaulingData.CENTER, GlobalPos.of(maid.level().dimension(), maid.blockPosition()));
        HaulingData.status(maid, "searching");
    }
    @Override public void onDeselected(AiMaidEntity maid) { WorkDefense.clearCombat(maid); }
    @Override public Component getStatus(AiMaidEntity maid) {
        return maid.getTarget() != null ? CombatStatus.describe(maid) : HaulingData.status(maid);
    }
    @Override public void buildServerCommand(LiteralArgumentBuilder<CommandSourceStack> node,
            CommandBuildContext build, MaidTargetResolver resolver) {
        node.executes(c -> MaidCommandCommandApi.executeSelection(c, resolver, this));
        node.then(Commands.argument("radius", IntegerArgumentType.integer(2, 32)).executes(c -> {
            var maid = MaidCommandCommandApi.resolveSelectableTarget(c, resolver, this);
            int result = MaidCommandCommandApi.finishSelection(c, maid, this);
            if (result > 0) maid.setAttached(HaulingData.RADIUS, IntegerArgumentType.getInteger(c, "radius"));
            return result;
        }));
    }
}

package io.github.ozozorz.aimaid.integration.command;

import net.minecraft.network.chat.Component;
import io.github.ozozorz.aimaid.entity.inventory.MaidEquipmentRules;
import io.github.ozozorz.aimaid.entity.combat.CombatStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;

import io.github.ozozorz.aimaid.command.MaidCommandCommandApi;
import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetResolver;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.RequireEquipment;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.integration.ai.FixtureActivities;
import io.github.ozozorz.aimaid.integration.ai.FixtureMemories;
import io.github.ozozorz.aimaid.integration.ai.behavior.ChoosePatrolTarget;
import io.github.ozozorz.aimaid.integration.ai.behavior.WalkToPatrolTarget;
import io.github.ozozorz.aimaid.integration.data.FixtureData;
import io.github.ozozorz.aimaid.entity.combat.WorkDefense;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.RandomLookAround;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

public class FixturePatrolCommand implements MaidCommand {
    @Override public boolean isVisibleInCommandMenu() { return false; }
    @Override public io.github.ozozorz.aimaid.entity.combat.MaidCombatMode getCombatMode() { return io.github.ozozorz.aimaid.entity.combat.MaidCombatMode.SELF_DEFENSE; }
    @Override public void onCombatEnded(AiMaidEntity maid, long now) {
        if (!maid.getMainHandItem().is(ItemTags.AXES) && maid.getOffhandItem().is(ItemTags.AXES))
            io.github.ozozorz.aimaid.entity.inventory.MaidItemTransfer.swapHands(maid);
    }

    @Override
    public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of(createPatrolActivity());
    }

    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return List.of(FixtureActivities.GUARD_FIGHT, FixtureActivities.PATROL, Activity.IDLE);
    }

    @Override
    public int getMenuOrder() {
        return 400;
    }

    @Override public Component getStatus(AiMaidEntity maid) {
        if (maid.getTarget() != null) return CombatStatus.describe(maid);
        if (!maid.getMainHandItem().is(ItemTags.AXES)) {
            if (!MaidEquipmentRules.canRemove(maid, EquipmentSlot.MAINHAND, null))
                return Component.translatable("status.testaddon.equipment_locked");
            if (maid.getInventory().findFirstMatchingSlot(s -> s.is(ItemTags.AXES)).isEmpty())
                return Component.translatable("status.testaddon.patrol.no_tool");
        }
        if (maid.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                .map(t -> maid.level().getGameTime() - t > 40).orElse(false))
            return Component.translatable("status.ai-maid.combat.target_unreachable");
        return Component.empty();
    }

    @Override
    public void buildServerCommand(LiteralArgumentBuilder<CommandSourceStack> node, CommandBuildContext buildContext,
                                   MaidTargetResolver targetResolver) {

        // 不带参数：
        //
        // /maid command testaddon:patrol
        //
        // 使用这只 Maid 已经保存的 radius；
        // 如果从来没设置过，就使用默认 4。
        node.executes(context -> MaidCommandCommandApi.executeSelection(context, targetResolver, this));

        // 带 radius：
        //
        // /maid command testaddon:patrol 4
        node.then(
                Commands.argument("radius", IntegerArgumentType.integer(2, 32))
                        .executes(context -> {

                            int radius = IntegerArgumentType.getInteger(context, "radius");

                            AiMaidEntity maid = MaidCommandCommandApi.resolveSelectableTarget(context, targetResolver,
                                    this);

                            int result = MaidCommandCommandApi.finishSelection(context, maid, this);
                            if (result > 0) FixtureData.setPatrolRadius(maid, radius);
                            return result;
                        })
        );

    }

    @Override
    public void onSelected(AiMaidEntity maid) {
        FixtureData.setPatrolCenter(maid);
    }

    @Override
    public void onDeselected(AiMaidEntity maid) {
        WorkDefense.clearCombat(maid);
        maid.getBrain().eraseMemory(FixtureMemories.PATROL_TARGET);
        maid.getBrain().eraseMemory(FixtureMemories.PATROL_PAUSE);
    }

    private ActivityData<AiMaidEntity> createPatrolActivity() {
        return ActivityData.create(
                FixtureActivities.PATROL,

                ActivityData.createPriorityPairs(
                        10,
                        ImmutableList.of(
                                RequireEquipment.create(EquipmentSlot.MAINHAND, PATROL_REQUIRED_TOOL, ChoosePatrolTarget.create()),
                                RequireEquipment.create(EquipmentSlot.MAINHAND, PATROL_REQUIRED_TOOL, WalkToPatrolTarget.create(0.7F)),
                                createPatrolAmbientBehaviors()
                        )
                ),

                Set.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)),

                Set.of(
                        MemoryModuleType.WALK_TARGET,
                        MemoryModuleType.LOOK_TARGET
                )
        );
    }

    private RunOne<AiMaidEntity> createPatrolAmbientBehaviors() {
        return new RunOne<>(
                Map.of(
                        FixtureMemories.PATROL_PAUSE,
                        MemoryStatus.VALUE_PRESENT
                ),
                ImmutableList.of(
                        Pair.of(new RandomLookAround(UniformInt.of(20, 50), 60.0F, -20.0F, 25.0F), 3),
                        Pair.of(new DoNothing(20, 40), 4)
                )
        );

    }

    private static final Predicate<ItemStack> PATROL_REQUIRED_TOOL = stack -> stack.is(ItemTags.AXES);

}

package io.github.ozozorz.testaddon.command;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
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
import io.github.ozozorz.testaddon.ai.TestAddonActivities;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.ai.behavior.TestChoosePatrolTarget;
import io.github.ozozorz.testaddon.ai.behavior.TestWalkToPatrolTarget;
import io.github.ozozorz.testaddon.data.TestAddonMaidData;
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

public class TestPatrolCommand implements MaidCommand {

    @Override
    public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of(createPatrolActivity());
    }

    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return List.of(TestAddonActivities.PATROL, Activity.IDLE);
    }

    @Override
    public int getMenuOrder() {
        return 400;
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

                            TestAddonMaidData.setPatrolRadius(maid, radius);

                            return MaidCommandCommandApi.finishSelection(context, maid, this);
                        })
        );

    }

    @Override
    public void onSelected(AiMaidEntity maid) {
        TestAddonMaidData.setPatrolCenter(maid);
    }

    private ActivityData<AiMaidEntity> createPatrolActivity() {
        return ActivityData.create(
                TestAddonActivities.PATROL,

                ActivityData.createPriorityPairs(
                        10,
                        ImmutableList.of(
                                RequireEquipment.create(EquipmentSlot.MAINHAND, PATROL_REQUIRED_TOOL, TestChoosePatrolTarget.create()),
                                RequireEquipment.create(EquipmentSlot.MAINHAND, PATROL_REQUIRED_TOOL, TestWalkToPatrolTarget.create(0.7F)),
                                createPatrolAmbientBehaviors()
                        )
                ),

                Set.of(),

                Set.of(
                        TestAddonMemoryModuleTypes.PATROL_TARGET,
                        TestAddonMemoryModuleTypes.PATROL_PAUSE,
                        MemoryModuleType.WALK_TARGET,
                        MemoryModuleType.LOOK_TARGET
                )
        );
    }

    private RunOne<AiMaidEntity> createPatrolAmbientBehaviors() {
        return new RunOne<>(
                Map.of(
                        TestAddonMemoryModuleTypes.PATROL_PAUSE,
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

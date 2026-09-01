package io.github.ozozorz.testaddon.command;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.RequireEquipment;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import io.github.ozozorz.testaddon.ai.TestAddonActivities;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.ai.behavior.TestBreakLumberTarget;
import io.github.ozozorz.testaddon.ai.behavior.TestFindLumberTarget;
import io.github.ozozorz.testaddon.ai.behavior.TestWalkToLumberTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

public class TestLumberCommand implements MaidCommand {

    private static final Predicate<ItemStack> REQUIRED_AXE = stack -> stack.is(ItemTags.AXES);

    
    @Override
    public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of(createLumberActivity());
    }


    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity arg0) {
        return List.of(
            // 工作没完成时优先工作
            TestAddonActivities.LUMBER,

            // 砍完以后允许主动捡掉落
            ModActivities.PICK_UP_ITEM,

            Activity.IDLE
        );
    }

    @Override
    public int getMenuOrder() {
        return 500;
    }

    @Override
    public void onSelected(AiMaidEntity maid) {
        /*
         * 每次重新选择 lumber，
         * 开始一轮新的“一块原木”工作。
         */
        Brain<AiMaidEntity> brain = maid.getBrain();
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_DONE);
        brain.eraseMemory(TestAddonMemoryModuleTypes.LUMBER_TARGET);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    @Override
    public boolean allowsItemPickup(AiMaidEntity maid, ServerLevel level, ItemStack itemStack) {
        return true;
    }

    private ActivityData<AiMaidEntity> createLumberActivity() {
        return ActivityData.create(
            TestAddonActivities.LUMBER, 
            
            ActivityData.createPriorityPairs(10, 
                ImmutableList.of(
                    /*
                     * 三个工作步骤都要求 MAINHAND axe。
                     *
                     * RequireEquipment 会：
                     * inventory -> equipment
                     * 自动换上斧头。
                     */
                    RequireEquipment.create(EquipmentSlot.MAINHAND, REQUIRED_AXE, 
                        TestFindLumberTarget.create(8, 4)
                    ), 
                    RequireEquipment.create(EquipmentSlot.MAINHAND, REQUIRED_AXE, 
                        TestWalkToLumberTarget.create(0.7F)
                    ), 
                    RequireEquipment.create(EquipmentSlot.MAINHAND, REQUIRED_AXE, 
                        TestBreakLumberTarget.create()
                    )
                )
            ), 
            
            /*
             * LUMBER_DONE 一旦出现，
             * LUMBER Activity 就失效。
             */
            Set.of(
                Pair.of(TestAddonMemoryModuleTypes.LUMBER_DONE, MemoryStatus.VALUE_ABSENT)
            ), 
            
            /*
             * Activity 停止时清掉临时工作现场。
             *
             * 注意：
             * 故意不清 LUMBER_DONE！
             */
            Set.of(
                TestAddonMemoryModuleTypes.LUMBER_TARGET,
                MemoryModuleType.WALK_TARGET,
                MemoryModuleType.LOOK_TARGET,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
            )

        );
    }

}

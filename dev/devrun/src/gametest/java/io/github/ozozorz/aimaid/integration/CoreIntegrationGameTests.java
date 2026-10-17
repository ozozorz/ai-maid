package io.github.ozozorz.aimaid.integration;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import io.github.ozozorz.aimaid.integration.ai.FixtureMemories;
import io.github.ozozorz.aimaid.integration.command.FixtureCommands;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

/** 新的 Core 规则在现有 Addon 中的运行链验收。 */
public class CoreIntegrationGameTests {
    private AiMaidEntity maid(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        var maid = h.spawn(ModEntityTypes.AI_MAID, 3.5F, 1, 3.5F);
        maid.setTame(true, true); return maid;
    }

    @GameTest(maxTicks = 120) public void lumberFailureExplainsAndReselectClearsReason(GameTestHelper h) {
        var maid = maid(h); h.setBlock(4, 1, 3, Blocks.OAK_LOG);
        maid.selectMaidCommand(FixtureCommands.LUMBER);
        h.runAfterDelay(10, () -> {
            var expected = Component.translatable("status.ai-maid-lumber.lumber.no_tool", 0, 0);
            h.assertTrue(maid.getBrain().hasMemoryValue(FixtureMemories.LUMBER_DONE)
                    && maid.getMaidStatus().equals(expected), "Missing tool did not reach GUI status source");
            for (int i = 0; i < 20; i++) maid.getMaidStatus();
            h.assertTrue(maid.getBrain().hasMemoryValue(FixtureMemories.LUMBER_DONE), "Status query restarted work");
            maid.getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
            maid.selectMaidCommand(FixtureCommands.LUMBER);
            maid.getBrain().setMemory(FixtureMemories.LUMBER_REQUEST, true);
            h.assertTrue(!maid.getBrain().hasMemoryValue(FixtureMemories.LUMBER_RESULT), "Reselect kept old reason");
            h.succeedWhen(() -> h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new net.minecraft.core.BlockPos(4, 1, 3))).isAir(), "Retry did not complete real work"));
        });
    }

    @GameTest(maxTicks = 40) public void lockedHandStopsWorkWithoutMovingAnyTool(GameTestHelper h) {
        var maid = maid(h); var sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
        maid.setItemSlot(EquipmentSlot.MAINHAND, sword); maid.getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
        maid.selectMaidCommand(FixtureCommands.LUMBER);
        h.runAfterDelay(10, () -> {
            h.assertTrue(maid.getMainHandItem() == sword && maid.getInventory().getItem(0).is(Items.IRON_AXE)
                    && maid.getBrain().hasMemoryValue(FixtureMemories.LUMBER_DONE), "Work bypassed equipment restriction"); h.succeed();
        });
    }

    @GameTest public void patrolMissingToolAndFullStorageAreVisible(GameTestHelper h) {
        var maid = maid(h); maid.selectMaidCommand(FixtureCommands.PATROL);
        h.assertTrue(maid.getMaidStatus().equals(Component.translatable("status.testaddon.patrol.no_tool")), "Patrol did not explain axe requirement");
        for (int i = 0; i < 35; i++) maid.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        h.assertTrue(maid.getMaidStatus().equals(Component.translatable("status.testaddon.patrol.no_tool").append("; ")
                .append(Component.translatable("status.ai-maid.storage_full"))), "Full storage message missing"); h.succeed();
    }

    @GameTest public void combatNoAmmoStatusDoesNotConsumeOrSwapItems(GameTestHelper h) {
        var maid = maid(h); maid.selectMaidCommand(FixtureCommands.GUARD);
        var bow = new ItemStack(Items.BOW); maid.setItemSlot(EquipmentSlot.MAINHAND, bow);
        var enemy = h.spawn(EntityTypes.ZOMBIE, 5.5F, 1, 3.5F); enemy.setNoAi(true);
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, enemy);
        for (int i = 0; i < 20; i++) h.assertTrue(maid.getMaidStatus().equals(Component.translatable("status.ai-maid.combat.no_ammo")), "No-ammo status missing");
        h.assertTrue(maid.getMainHandItem() == bow && bow.getDamageValue() == 0, "Status query changed equipment"); h.succeed();
    }

    @GameTest(maxTicks = 80) public void ownerlessGuardUsesCoreWaitingActivity(GameTestHelper h) {
        var maid = maid(h); var start = maid.position(); maid.selectMaidCommand(FixtureCommands.GUARD);
        h.runAfterDelay(50, () -> {
            h.assertTrue(maid.getBrain().isActive(ModActivities.WAIT_FOR_OWNER) && maid.position().distanceToSqr(start) < 0.2, "Guard wandered without owner"); h.succeed();
        });
    }
}

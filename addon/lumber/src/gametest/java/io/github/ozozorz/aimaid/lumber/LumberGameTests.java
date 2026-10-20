package io.github.ozozorz.aimaid.lumber;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidBlockBreaking;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.lumber.ai.LumberMemories;
import io.github.ozozorz.aimaid.lumber.LumberAddon;
import io.github.ozozorz.aimaid.lumber.lumber.LumberTreeFinder;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public class LumberGameTests {
    private AiMaidEntity worker(GameTestHelper h, Item axe, float x, float z) {
        for (int bx = 0; bx < 8; bx++) for (int bz = 0; bz < 8; bz++) h.setBlock(bx, 0, bz, Blocks.DIRT);
        var maid = h.spawn(ModEntityTypes.AI_MAID, x, 1, z);
        maid.setTame(true, true);
        maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(axe));
        return maid;
    }

    private void single(AiMaidEntity maid) {
        maid.selectMaidCommand(LumberAddon.LUMBER);
        maid.getBrain().setMemory(LumberMemories.LUMBER_REQUEST, true);
    }

    private void tree(GameTestHelper h, int x, int z, int height, boolean persistent) {
        for (int y = 1; y <= height; y++) h.setBlock(x, y, z, Blocks.OAK_LOG);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) h.setBlock(new BlockPos(x + dx, height, z + dz),
                    Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, persistent).setValue(LeavesBlock.DISTANCE, 1));
        }
    }

    private boolean done(AiMaidEntity maid) { return maid.getBrain().hasMemoryValue(LumberMemories.LUMBER_DONE); }
    private int logs(GameTestHelper h, AiMaidEntity maid) {
        return maid.getInventory().countMatching(s -> s.is(Items.OAK_LOG))
                + h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds()).stream()
                .filter(e -> e.getItem().is(Items.OAK_LOG)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    @GameTest(maxTicks = 180)
    public void singleLogTakesTimeAndDropsExactlyOnce(GameTestHelper h) {
        var maid = worker(h, Items.WOODEN_AXE, 2.5F, 4.5F);
        h.setBlock(4, 1, 4, Blocks.OAK_LOG);
        single(maid);
        h.runAfterDelay(10, () -> {
            h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 1, 4));
            h.assertTrue(maid.getMainHandItem().getDamageValue() == 0 && logs(h, maid) == 0, "Unfinished mining must not consume durability or create loot");
        });
        h.succeedWhen(() -> {
            h.assertTrue(done(maid), "Single-block job has not finished");
            h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 1, 4));
            h.assertTrue(logs(h, maid) == 1 && maid.getMainHandItem().getDamageValue() == 1, "One completed block must produce exactly one loot/durability transaction");
        });
    }

    @GameTest(maxTicks = 180)
    public void betterToolBreaksFaster(GameTestHelper h) {
        var wood = worker(h, Items.WOODEN_AXE, 1.5F, 1.5F);
        var diamond = worker(h, Items.DIAMOND_AXE, 6.5F, 6.5F);
        h.setBlock(1, 1, 3, Blocks.OAK_LOG);
        h.setBlock(6, 1, 4, Blocks.OAK_LOG);
        single(wood); single(diamond);
        long[] times = {-1, -1};
        h.onEachTick(() -> {
            if (times[0] < 0 && h.getLevel().getBlockState(h.absolutePos(new BlockPos(1, 1, 3))).isAir()) times[0] = h.getTick();
            if (times[1] < 0 && h.getLevel().getBlockState(h.absolutePos(new BlockPos(6, 1, 4))).isAir()) times[1] = h.getTick();
        });
        h.succeedWhen(() -> h.assertTrue(times[0] > 0 && times[1] > 0 && times[1] < times[0], "Tool speed did not change real completion time"));
    }

    @GameTest(maxTicks = 180)
    public void toolReplacementRestartsProgress(GameTestHelper h) {
        var maid = worker(h, Items.DIAMOND_AXE, 2.5F, 4.5F);
        var diamond = maid.getMainHandItem();
        h.setBlock(4, 1, 4, Blocks.OAK_LOG);
        single(maid);
        boolean[] changed = {false};
        h.onEachTick(() -> {
            if (!changed[0] && maid.swinging) {
                changed[0] = true;
                maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
                h.runAfterDelay(20, () -> h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 1, 4)));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(changed[0] && done(maid), "Replacement job must finish");
            h.assertTrue(diamond.getDamageValue() == 0 && maid.getMainHandItem().getDamageValue() == 1, "Wrong tool was charged durability");
        });
    }

    @GameTest(maxTicks = 100)
    public void cancellingDuringMiningKeepsBlockAndTool(GameTestHelper h) {
        var maid = worker(h, Items.WOODEN_AXE, 2.5F, 4.5F);
        h.setBlock(4, 1, 4, Blocks.OAK_LOG);
        single(maid);
        h.runAfterDelay(10, () -> {
            h.assertTrue(maid.swinging, "Cancellation must interrupt actual mining");
            maid.selectMaidCommand(MaidCommands.STAY);
        });
        h.runAfterDelay(65, () -> {
            h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 1, 4));
            h.assertTrue(maid.getMainHandItem().getDamageValue() == 0 && logs(h, maid) == 0, "Cancelled mining changed items");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void changedBlockIsNotDestroyed(GameTestHelper h) {
        var maid = worker(h, Items.WOODEN_AXE, 2.5F, 4.5F);
        h.setBlock(4, 1, 4, Blocks.OAK_LOG);
        single(maid);
        h.runAfterDelay(10, () -> {
            h.assertTrue(maid.swinging, "Replacement must interrupt actual mining");
            h.setBlock(4, 1, 4, Blocks.STONE);
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && done(maid), "Changed target must finish without hanging");
            h.assertBlockPresent(Blocks.STONE, new BlockPos(4, 1, 4));
            h.assertTrue(maid.getMainHandItem().getDamageValue() == 0, "Changed block consumed tool durability");
        });
    }

    @GameTest(maxTicks = 100)
    public void missingToolEndsJob(GameTestHelper h) {
        var maid = worker(h, Items.WOODEN_AXE, 2.5F, 4.5F);
        h.setBlock(4, 1, 4, Blocks.OAK_LOG);
        single(maid);
        h.runAfterDelay(10, () -> {
            h.assertTrue(maid.swinging, "Tool removal must interrupt actual mining");
            maid.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && done(maid), "No tool must end the job");
            h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 1, 4));
        });
    }

    @GameTest(maxTicks = 250)
    public void entireReachableTreeIsProcessedSequentially(GameTestHelper h) {
        var maid = worker(h, Items.IRON_AXE, 2.5F, 4.5F);
        tree(h, 4, 4, 4, false);
        maid.selectMaidCommand(LumberAddon.LUMBER);
        h.runAfterDelay(6, () -> h.assertTrue(logs(h, maid) == 0, "Tree must not disappear in one instant"));
        h.succeedWhen(() -> {
            h.assertTrue(done(maid), "Tree job did not finish");
            for (int y = 1; y <= 4; y++) h.assertBlockPresent(Blocks.AIR, new BlockPos(4, y, 4));
            h.assertTrue(logs(h, maid) == 4 && maid.getMainHandItem().getDamageValue() == 4, "Continuous tree harvesting lost/duplicated items");
        });
    }

    @GameTest(maxTicks = 400)
    public void diagonalApproachFellsEveryLogWithoutSkippingTheCanopy(GameTestHelper h) {
        var maid = worker(h, Items.IRON_AXE, 2.5F, 7.5F);
        for (int y = 1; y <= 3; y++) h.setBlock(6, y, 2, Blocks.OAK_LOG);
        for (int x = 5; x <= 7; x++) for (int z = 1; z <= 3; z++)
            h.setBlock(new BlockPos(x, 4, z), Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1));
        maid.selectMaidCommand(LumberAddon.LUMBER);
        h.succeedWhen(() -> {
            h.assertTrue(done(maid), "Diagonal approach did not finish");
            for (int y = 1; y <= 3; y++) h.assertBlockPresent(Blocks.AIR, new BlockPos(6, y, 2));
            h.assertTrue(logs(h, maid) == 3 && maid.getMainHandItem().getDamageValue() == 3,
                    "Stopping at a stand's edge must not skip a reachable log");
        });
    }

    @GameTest(maxTicks = 400)
    public void unreachableCanopyIsSkippedWithoutRemoteBreaking(GameTestHelper h) {
        var maid = worker(h, Items.IRON_AXE, 2.5F, 4.5F);
        tree(h, 4, 4, 12, false);
        maid.selectMaidCommand(LumberAddon.LUMBER);
        h.succeedWhen(() -> {
            h.assertTrue(done(maid), "Unreachable canopy must not hang the job");
            for (int y = 1; y <= 6; y++) h.assertBlockPresent(Blocks.AIR, new BlockPos(4, y, 4));
            for (int y = 7; y <= 12; y++) h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, y, 4));
            h.assertTrue(logs(h, maid) == 6, "Must harvest reachable logs and preserve the entire unreachable canopy");
        });
    }

    @GameTest(maxTicks = 100)
    public void artificialLeafCanopyDoesNotAuthorizeTreeFelling(GameTestHelper h) {
        var maid = worker(h, Items.IRON_AXE, 2.5F, 4.5F);
        tree(h, 4, 4, 4, true);
        maid.selectMaidCommand(LumberAddon.LUMBER);
        h.succeedWhen(() -> {
            h.assertTrue(done(maid), "No natural tree must end the request");
            h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 1, 4));
            h.assertTrue(logs(h, maid) == 0, "Artificial structure was harvested");
        });
    }

    @GameTest
    public void treeRecognitionRejectsBarePillarsAndOversizedConnections(GameTestHelper h) {
        worker(h, Items.IRON_AXE, 2.5F, 4.5F);
        for (int y = 1; y <= 4; y++) h.setBlock(4, y, 4, Blocks.OAK_LOG);
        h.assertTrue(LumberTreeFinder.collect(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4))).isEmpty(), "Bare log pillar must not be a natural tree");
        tree(h, 4, 4, 33, false);
        h.assertTrue(LumberTreeFinder.collect(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4))).isEmpty(), "Oversized tree must be rejected, not truncated");
        h.succeed();
    }

    @GameTest
    public void blockMechanismChecksRangeSightHardnessAndGameRule(GameTestHelper h) {
        var maid = worker(h, Items.IRON_AXE, 1.5F, 4.5F);
        h.setBlock(6, 1, 4, Blocks.OAK_LOG);
        var far = h.absolutePos(new BlockPos(6, 1, 4));
        h.assertTrue(!MaidBlockBreaking.destroy(h.getLevel(), maid, far, h.getLevel().getBlockState(far), maid.getMainHandItem()), "Must not break outside real reach");
        h.setBlock(3, 1, 4, Blocks.OAK_LOG);
        var near = h.absolutePos(new BlockPos(3, 1, 4));
        h.setBlock(2, 1, 4, Blocks.STONE);
        h.setBlock(2, 2, 4, Blocks.STONE);
        h.assertTrue(!MaidBlockBreaking.destroy(h.getLevel(), maid, near, h.getLevel().getBlockState(near), maid.getMainHandItem()), "Must not break through a wall");
        h.setBlock(2, 1, 4, Blocks.AIR);
        h.setBlock(2, 2, 4, Blocks.AIR);
        var rules = h.getLevel().getGameRules();
        boolean old = rules.get(GameRules.MOB_GRIEFING);
        try {
            rules.set(GameRules.MOB_GRIEFING, false, h.getLevel().getServer());
            h.assertTrue(!MaidBlockBreaking.destroy(h.getLevel(), maid, near, h.getLevel().getBlockState(near), maid.getMainHandItem()), "mobGriefing must be checked at commit");
        } finally { rules.set(GameRules.MOB_GRIEFING, old, h.getLevel().getServer()); }
        h.setBlock(3, 1, 4, Blocks.BEDROCK);
        h.assertTrue(MaidBlockBreaking.progressPerTick(h.getLevel(), maid, near, h.getLevel().getBlockState(near), maid.getMainHandItem()) == 0, "Unbreakable hardness must never progress");
        h.succeed();
    }

    @GameTest(maxTicks = 250)
    public void walksIntoRealReachBeforeMining(GameTestHelper h) {
        var maid = worker(h, Items.IRON_AXE, 1.5F, 4.5F);
        var start = maid.position();
        h.setBlock(6, 1, 4, Blocks.OAK_LOG);
        single(maid);
        h.onEachTick(() -> {
            if (maid.swinging) h.assertTrue(MaidBlockBreaking.canReach(h.getLevel(), maid,
                    h.absolutePos(new BlockPos(6, 1, 4))) || h.getLevel().getBlockState(h.absolutePos(new BlockPos(6, 1, 4))).isAir(),
                    "Mining began outside real reach");
        });
        h.succeedWhen(() -> {
            h.assertTrue(done(maid), "Navigation job did not finish");
            h.assertBlockPresent(Blocks.AIR, new BlockPos(6, 1, 4));
            h.assertTrue(maid.position().distanceToSqr(start) > 0.5 && logs(h, maid) == 1, "Maid must walk, not mine remotely");
        });
    }

    @GameTest(maxTicks = 250)
    public void brokenAxeIsReplacedFromStorageForNextLog(GameTestHelper h) {
        var maid = worker(h, Items.WOODEN_AXE, 2.5F, 4.5F);
        maid.getMainHandItem().setDamageValue(maid.getMainHandItem().getMaxDamage() - 1);
        maid.getInventory().setItem(34, new ItemStack(Items.IRON_AXE));
        tree(h, 4, 4, 4, false);
        maid.selectMaidCommand(LumberAddon.LUMBER);
        h.succeedWhen(() -> {
            h.assertTrue(done(maid), "Replacement axe job did not finish");
            h.assertTrue(logs(h, maid) == 4, "Tool breaking lost the remaining tree queue");
            h.assertTrue(maid.getMainHandItem().is(Items.IRON_AXE) && maid.getMainHandItem().getDamageValue() == 3,
                    "Replacement must be used only for the remaining three logs");
        });
    }

    @GameTest(maxTicks = 180)
    public void twoMaidsCannotDuplicateOneLog(GameTestHelper h) {
        var first = worker(h, Items.WOODEN_AXE, 2.5F, 3.5F);
        var second = worker(h, Items.WOODEN_AXE, 2.5F, 5.5F);
        h.setBlock(4, 1, 4, Blocks.OAK_LOG);
        single(first); single(second);
        h.succeedWhen(() -> {
            h.assertTrue(done(first) && done(second), "Concurrent jobs did not finish");
            h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 1, 4));
            h.assertTrue(logs(h, first) + second.getInventory().countMatching(s -> s.is(Items.OAK_LOG)) == 1,
                    "Concurrent workers duplicated loot");
            h.assertTrue(first.getMainHandItem().getDamageValue() + second.getMainHandItem().getDamageValue() == 1,
                    "The losing worker consumed durability");
        });
    }

    @GameTest(maxTicks = 100)
    public void reloadKeepsCommandButDoesNotResumeStaleJob(GameTestHelper h) {
        var maid = worker(h, Items.WOODEN_AXE, 2.5F, 4.5F);
        var owner = h.spawn(EntityTypes.VILLAGER, 1.5F, 1, 1.5F);
        owner.setNoAi(true);
        maid.setOwner(owner);
        h.setBlock(4, 1, 4, Blocks.OAK_LOG);
        single(maid);
        h.runAfterDelay(10, () -> {
            h.assertTrue(maid.swinging, "Save must happen during actual mining");
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
            maid.saveWithoutId(output);
            maid.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var restored = new AiMaidEntity(h.getLevel());
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
            h.getLevel().addFreshEntity(restored);
            h.runAfterDelay(50, () -> {
                h.assertTrue(restored.getMaidCommand() == LumberAddon.LUMBER && done(restored), "Saved command must become an idle finished request");
                h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 1, 4));
                h.assertTrue(restored.getMainHandItem().getDamageValue() == 0, "Reload resumed stale mining progress");
                h.succeed();
            });
        });
    }

    @GameTest(maxTicks = 180)
    public void efficiencyEnchantmentAffectsRealMiningTime(GameTestHelper h) {
        var normal = worker(h, Items.WOODEN_AXE, 1.5F, 1.5F);
        var enchanted = worker(h, Items.WOODEN_AXE, 6.5F, 6.5F);
        enchanted.getMainHandItem().enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.EFFICIENCY), 3);
        h.setBlock(1, 1, 3, Blocks.OAK_LOG);
        h.setBlock(6, 1, 4, Blocks.OAK_LOG);
        single(normal); single(enchanted);
        long[] times = {-1, -1};
        h.onEachTick(() -> {
            if (times[0] < 0 && h.getLevel().getBlockState(h.absolutePos(new BlockPos(1, 1, 3))).isAir()) times[0] = h.getTick();
            if (times[1] < 0 && h.getLevel().getBlockState(h.absolutePos(new BlockPos(6, 1, 4))).isAir()) times[1] = h.getTick();
        });
        h.succeedWhen(() -> h.assertTrue(times[0] > 0 && times[1] > 0 && times[1] < times[0], "Efficiency enchantment must affect actual ticks"));
    }

    @GameTest
    public void recognizesTwoByTwoTrunkButRejectsConnectedSeparateRoots(GameTestHelper h) {
        worker(h, Items.IRON_AXE, 1.5F, 4.5F);
        for (int x = 4; x <= 5; x++) for (int z = 4; z <= 5; z++) tree(h, x, z, 5, false);
        // 后生成的树冠会覆盖相邻顶部，重填 2×2 树干。
        for (int x = 4; x <= 5; x++) for (int z = 4; z <= 5; z++) for (int y = 1; y <= 5; y++) h.setBlock(x, y, z, Blocks.OAK_LOG);
        var root = h.absolutePos(new BlockPos(4, 1, 4));
        var tree = LumberTreeFinder.collect(h.getLevel(), root);
        h.assertTrue(tree.isPresent() && tree.get().size() == 20, "A complete 2x2 trunk must be one tree");
        for (int y = 1; y <= 4; y++) h.setBlock(6, y, 5, Blocks.OAK_LOG);
        h.assertTrue(LumberTreeFinder.collect(h.getLevel(), root).isEmpty(), "Extra rooted trunk must reject the connected group");
        h.succeed();
    }
}

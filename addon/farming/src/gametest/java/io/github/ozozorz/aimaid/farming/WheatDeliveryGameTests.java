package io.github.ozozorz.aimaid.farming;

import io.github.ozozorz.aimaid.entity.*;
import io.github.ozozorz.aimaid.entity.ai.AiMaidAi;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.task.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/** Real Brain ticks and world/inventory assertions, including partial effects and interruption. */
public class WheatDeliveryGameTests {
    // Keep the 16-block observation area isolated from other concurrently running tests.
    private static final String AREA = "ai-maid-farming-tests:delivery";
    private static final BlockPos CHEST = new BlockPos(23, 1, 23);
    private record Scene(AiMaidEntity maid, Mob owner, WheatDeliveryTask task, Container chest) {}

    private Scene scene(GameTestHelper h, int count, int initial, int crops) {
        for (int x = 15; x <= 26; x++) for (int z = 15; z <= 26; z++) h.setBlock(x, 0, z, Blocks.DIRT);
        var owner = h.spawn(EntityTypes.VILLAGER, 17.5F, 1, 17.5F); owner.setNoAi(true);
        var maid = h.spawn(ModEntityTypes.AI_MAID, 18.5F, 1, 18.5F);
        maid.setTame(true, true); maid.setOwner(owner);
        if (initial > 0) maid.getInventory().setItem(0, new ItemStack(Items.WHEAT, initial));
        // Guarantee replanting independently of the random seed drop count.
        maid.getInventory().setItem(1, new ItemStack(Items.WHEAT_SEEDS, 16));
        for (int i = 0; i < crops; i++) {
            var p = new BlockPos(19 + i % 4, 1, 20 + i / 4);
            h.setBlock(p.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
            h.setBlock(p, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        }
        h.setBlock(CHEST, Blocks.CHEST);
        maid.selectMaidCommand(FarmingAddon.DELIVERY);
        var task = new WheatDeliveryTask(maid, count, h.absolutePos(CHEST));
        MaidTasks.install(maid, task); AiMaidAi.updateActivity(maid);
        return new Scene(maid, owner, task, h.getBlockEntity(CHEST, net.minecraft.world.level.block.entity.ChestBlockEntity.class));
    }
    private int chestWheat(Container chest) {
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.WHEAT)) total += chest.getItem(i).getCount();
        return total;
    }
    private void failed(GameTestHelper h, Scene s, String reason) {
        var snapshot = s.task().snapshot(s.maid());
        h.assertTrue(snapshot.state() == MaidTask.State.FAILED && snapshot.reason().equals(reason), "Expected " + reason + ", got " + snapshot);
    }

    @GameTest(structure = AREA, maxTicks = 500)
    public void gathersOnlyShortfallDepositsExactCountAndWalksBack(GameTestHelper h) {
        var s = scene(h, 5, 3, 4);
        boolean[] leftOwner = {false}, returned = {false};
        h.onEachTick(() -> {
            leftOwner[0] |= s.maid().distanceToSqr(s.owner()) > 16;
            if (s.task().snapshot(s.maid()).step().equals("return")) returned[0] = true;
        });
        h.succeedWhen(() -> {
            var result = s.task().snapshot(s.maid());
            h.assertTrue(result.state() == MaidTask.State.SUCCEEDED, "Task not complete: " + result);
            h.assertTrue(chestWheat(s.chest()) == 5 && WheatDeliveryTask.wheat(s.maid()) == 0, "Wrong delivered/inventory count");
            h.assertTrue(result.progress().get("harvested") == 2, "Did not use existing wheat first");
            h.assertTrue(leftOwner[0] && returned[0] && s.maid().distanceToSqr(s.owner()) <= 9, "No physical round trip");
            int mature = 0;
            for (int x = 19; x < 23; x++) if (((CropBlock) Blocks.WHEAT).isMaxAge(h.getBlockState(new BlockPos(x, 1, 20)))) mature++;
            h.assertTrue(mature == 2, "Over-harvested or failed to replant");
        });
    }

    @GameTest(structure = AREA, maxTicks = 400)
    public void existingSurplusIsKeptAndCropsAreUntouched(GameTestHelper h) {
        var s = scene(h, 3, 7, 1);
        h.succeedWhen(() -> {
            h.assertTrue(s.task().snapshot(s.maid()).state() == MaidTask.State.SUCCEEDED, "Not returned");
            h.assertTrue(chestWheat(s.chest()) == 3 && WheatDeliveryTask.wheat(s.maid()) == 4, "Deposited excess stock");
            h.assertTrue(((CropBlock) Blocks.WHEAT).isMaxAge(h.getBlockState(new BlockPos(19, 1, 20))), "Unnecessary harvest");
        });
    }

    @GameTest(structure = AREA, maxTicks = 200)
    public void insufficientCropsFailWithoutPretendingDelivery(GameTestHelper h) {
        var s = scene(h, 3, 1, 1);
        h.succeedWhen(() -> { failed(h, s, "insufficient_wheat");
            h.assertTrue(WheatDeliveryTask.wheat(s.maid()) == 2 && chestWheat(s.chest()) == 0, "Lost or invented wheat"); });
    }

    @GameTest(structure = AREA, maxTicks = 350)
    public void fullChestKeepsCargoAndReportsFailure(GameTestHelper h) {
        var s = scene(h, 2, 2, 0);
        for (int i = 0; i < s.chest().getContainerSize(); i++) s.chest().setItem(i, new ItemStack(Items.STONE, 64));
        h.succeedWhen(() -> { failed(h, s, "chest_full");
            h.assertTrue(WheatDeliveryTask.wheat(s.maid()) == 2 && chestWheat(s.chest()) == 0, "Full chest lost cargo"); });
    }

    @GameTest(structure = AREA, maxTicks = 400)
    public void partialDepositIsCountedOnceAndRemainderIsKept(GameTestHelper h) {
        var s = scene(h, 3, 3, 0);
        for (int i = 0; i < s.chest().getContainerSize(); i++) s.chest().setItem(i, new ItemStack(Items.STONE, 64));
        s.chest().setItem(0, new ItemStack(Items.WHEAT, 63));
        h.succeedWhen(() -> { failed(h, s, "chest_full");
            h.assertTrue(s.task().snapshot(s.maid()).progress().get("delivered") == 1, "Missing partial receipt");
            // Reticking a terminal task must never perform the transfer again.
            for (int i = 0; i < 4; i++) s.task().tick(s.maid(), h.getLevel().getGameTime() + i);
            h.assertTrue(chestWheat(s.chest()) == 64 && WheatDeliveryTask.wheat(s.maid()) == 2, "Partial delivery duplicated or lost items"); });
    }

    @GameTest(structure = AREA, maxTicks = 100)
    public void replacingDestinationDoesNotRedirectDelivery(GameTestHelper h) {
        var s = scene(h, 2, 2, 0);
        h.runAfterDelay(2, () -> { h.setBlock(CHEST, Blocks.AIR); h.setBlock(CHEST, Blocks.CHEST); });
        h.succeedWhen(() -> { failed(h, s, "chest_changed");
            h.assertTrue(WheatDeliveryTask.wheat(s.maid()) == 2 && chestWheat(h.getBlockEntity(CHEST, net.minecraft.world.level.block.entity.ChestBlockEntity.class)) == 0, "Used a substituted chest"); });
    }

    @GameTest(structure = AREA, maxTicks = 100)
    public void manualModeChangeCancelsAllRemainingSteps(GameTestHelper h) {
        var s = scene(h, 2, 0, 2);
        h.runAfterDelay(1, () -> s.maid().selectMaidCommand(MaidCommands.STAY));
        h.runAfterDelay(60, () -> {
            h.assertTrue(s.task().snapshot(s.maid()).state() == MaidTask.State.CANCELLED, "Manual takeover did not cancel task");
            h.assertTrue(WheatDeliveryTask.wheat(s.maid()) == 0 && chestWheat(s.chest()) == 0, "Cancelled task still mutated inventory");
            h.assertTrue(s.maid().getMaidCommand() == MaidCommands.STAY, "Task overrode manual command"); h.succeed();
        });
    }

    @GameTest(structure = AREA, maxTicks = 100)
    public void fullInventoryLeavesCropIntact(GameTestHelper h) {
        var s = scene(h, 1, 0, 1);
        for (int i = 0; i < s.maid().getInventory().getContainerSize(); i++) s.maid().getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        h.succeedWhen(() -> { failed(h, s, "inventory_full");
            h.assertTrue(((CropBlock) Blocks.WHEAT).isMaxAge(h.getBlockState(new BlockPos(19, 1, 20))), "Failed harvest destroyed crop"); });
    }

    @GameTest(structure = AREA, maxTicks = 100)
    public void cargoRemovedDuringTravelCannotBeDelivered(GameTestHelper h) {
        var s = scene(h, 2, 2, 0); boolean[] removed = {false};
        h.onEachTick(() -> {
            if (!removed[0] && s.task().snapshot(s.maid()).step().equals("deposit")) {
                removed[0] = true; s.maid().getInventory().removeItemNoUpdate(0);
            }
        });
        h.succeedWhen(() -> { failed(h, s, "inventory_changed"); h.assertTrue(chestWheat(s.chest()) == 0, "Invented missing cargo"); });
    }

    @GameTest(structure = AREA, maxTicks = 400)
    public void ownerUnavailableAfterDepositIsNotReportedAsSuccess(GameTestHelper h) {
        var s = scene(h, 2, 2, 0); boolean[] removed = {false};
        h.onEachTick(() -> {
            if (!removed[0] && s.task().snapshot(s.maid()).step().equals("return")) { removed[0] = true; s.owner().discard(); }
        });
        h.succeedWhen(() -> {
            h.assertTrue(s.task().snapshot(s.maid()).state() == MaidTask.State.CANCELLED, "Missing owner treated as successful return");
            h.assertTrue(chestWheat(s.chest()) == 2 && s.task().snapshot(s.maid()).progress().get("delivered") == 2, "Lost completed deposit receipt");
        });
    }

    @GameTest(structure = AREA, maxTicks = 200)
    public void sealedChestFailsWithoutRemoteInsertion(GameTestHelper h) {
        var s = scene(h, 2, 2, 0);
        for (int y = 1; y <= 4; y++) for (int x = 22; x <= 24; x++) for (int z = 22; z <= 24; z++)
            if (x != 23 || z != 23) h.setBlock(x, y, z, Blocks.STONE);
        h.succeedWhen(() -> { failed(h, s, "chest_unreachable");
            h.assertTrue(WheatDeliveryTask.wheat(s.maid()) == 2 && chestWheat(s.chest()) == 0, "Inserted through wall"); });
    }

    @GameTest(structure = AREA, maxTicks = 600)
    public void combatPausesAndResumesWithoutDuplicateEffects(GameTestHelper h) {
        var s = scene(h, 2, 0, 2);
        var enemy = h.spawn(EntityTypes.ZOMBIE, 16.5F, 1, 19.5F); enemy.setNoAi(true);
        enemy.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); enemy.setHealth(100);
        boolean[] paused = {false}; int[] harvestAtPause = {-1};
        h.runAfterDelay(5, () -> h.hurt(s.maid(), h.getLevel().damageSources().mobAttack(enemy), 1));
        h.onEachTick(() -> {
            var snap = s.task().snapshot(s.maid());
            if (snap.state() == MaidTask.State.PAUSED && !paused[0]) {
                paused[0] = true; harvestAtPause[0] = snap.progress().get("harvested");
                h.runAfterDelay(12, () -> {
                    h.assertTrue(s.task().snapshot(s.maid()).progress().get("harvested") == harvestAtPause[0], "Worked during combat");
                    enemy.discard();
                });
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(paused[0] && s.task().snapshot(s.maid()).state() == MaidTask.State.SUCCEEDED, "Task did not resume after combat");
            h.assertTrue(chestWheat(s.chest()) == 2 && WheatDeliveryTask.wheat(s.maid()) == 0, "Combat duplicated/lost wheat");
        });
    }

    @GameTest(structure = AREA, maxTicks = 100)
    public void unloadingCancelsAndSavedEntityDoesNotReplayTask(GameTestHelper h) {
        var s = scene(h, 2, 2, 0);
        h.runAfterDelay(2, () -> {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, h.getLevel().registryAccess());
            s.maid().saveWithoutId(output); s.maid().discard();
            h.assertTrue(s.task().snapshot(s.maid()).state() == MaidTask.State.CANCELLED, "Unload did not cancel task");
            var loaded = new AiMaidEntity(h.getLevel());
            loaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
            h.assertTrue(MaidTasks.current(loaded) == null && chestWheat(s.chest()) == 0, "Reload restored stale task or moved cargo"); h.succeed();
        });
    }
}

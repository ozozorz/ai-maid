package io.github.ozozorz.aimaid.integration;

import java.util.*;
import com.google.gson.*;
import io.github.ozozorz.aimaid.entity.*;
import io.github.ozozorz.aimaid.entity.ai.AiMaidAi;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.task.*;
import io.github.ozozorz.aimaid.llm.tool.*;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Real world effects plus the game-owned lifetime, independent of model availability. */
public class GoalActionGameTests {
    private static final String AREA = "ai-maid-integration-tests:delivery";
    private static final BlockPos CHEST = new BlockPos(24, 1, 23), TREE = new BlockPos(23, 1, 18);
    private record Scene(AiMaidEntity maid, ServerPlayer owner) {}
    private Scene scene(GameTestHelper h) {
        for (int x = 15; x <= 28; x++) for (int z = 15; z <= 28; z++) h.setBlock(x, 0, z, Blocks.DIRT);
        var maid = h.spawn(ModEntityTypes.AI_MAID, 18.5F, 1, 18.5F); maid.setNoAi(true);
        var owner = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(17.5, 1, 17.5)));
        maid.setTame(true, true); maid.setOwner(owner); maid.selectMaidCommand(MaidCommands.STAY);
        h.setBlock(CHEST, Blocks.CHEST); return new Scene(maid, owner);
    }
    private JsonObject step(String action) { var s = new JsonObject(); s.addProperty("action", action); return s; }
    private String observe(GameTestHelper h, Scene s, String kind) {
        var reply = MaidTools.execute("observe_nearby", new JsonObject(), s.maid(), s.owner(), 32);
        for (var value : reply.getAsJsonArray("targets")) {
            var row = value.getAsJsonObject(); if (row.get("kind").getAsString().equals(kind)) return row.get("target_id").getAsString();
        }
        throw new AssertionError("Missing " + kind + " observation: " + reply);
    }
    private MaidActions.Request parse(Scene s, JsonObject step) { return ActionTools.parse(step, s.maid(), s.owner()); }
    private MaidPlanTask plan(Scene s, JsonObject... steps) {
        var requests = Arrays.stream(steps).map(v -> parse(s, v)).toList();
        var task = new MaidPlanTask(s.maid(), UUID.randomUUID().toString(), requests);
        s.maid().selectMaidCommand(MaidCommands.PLAN); MaidTasks.install(s.maid(), task); AiMaidAi.updateActivity(s.maid());
        s.maid().setNoAi(false); return task;
    }
    private JsonObject deposit(String token, String item, int count) {
        var step = step("ai-maid:deposit_items"); step.addProperty("target_id", token); step.addProperty("item", item); step.addProperty("count", count); return step;
    }
    private MaidGoalTask goal(Scene s, MaidGoalTask.Limits limits) {
        var goal = new MaidGoalTask(s.maid(), "Complete only the requested work", limits);
        s.maid().selectMaidCommand(MaidCommands.PLAN); MaidTasks.install(s.maid(), goal); AiMaidAi.updateActivity(s.maid()); return goal;
    }
    private void tree(GameTestHelper h) {
        for (int y = 0; y < 3; y++) h.setBlock(TREE.above(y), Blocks.OAK_LOG);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            h.setBlock(TREE.offset(x, 3, z), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1));
    }
    @GameTest(structure = AREA, maxTicks = 400)
    public void carrotsAreHarvestedReplantedAndExactlyDeposited(GameTestHelper h) {
        var s = scene(h); var crop = new BlockPos(21, 1, 21);
        h.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
        h.setBlock(crop, ((CropBlock) Blocks.CARROTS).getStateForAge(7));
        var harvest = step("ai-maid-farming:harvest_crops"); harvest.addProperty("target_id", observe(h, s, "area"));
        harvest.addProperty("item", "minecraft:carrot"); harvest.addProperty("count", 1);
        var task = plan(s, harvest, deposit(observe(h, s, "chest"), "minecraft:carrot", 1));
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.SUCCEEDED, "Carrot sequence incomplete: " + task.snapshot(s.maid()));
            h.assertTrue(h.getBlockEntity(CHEST, ChestBlockEntity.class).countItem(Items.CARROT) == 1, "Wrong transfer");
            h.assertTrue(h.getBlockState(crop).is(Blocks.CARROTS) && !((CropBlock) Blocks.CARROTS).isMaxAge(h.getBlockState(crop)), "Not replanted");
            int gained = task.actions(s.maid()).getFirst().progress().get("harvested");
            h.assertTrue(gained == 1 + s.maid().getInventory().countMatching(i -> i.is(Items.CARROT)), "Harvest/deposit quantities not conserved");
        });
    }
    @GameTest(structure = AREA, maxTicks = 300)
    public void arbitraryItemDepositPreservesPartialReceipt(GameTestHelper h) {
        var s = scene(h); s.maid().getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
        var chest = h.getBlockEntity(CHEST, ChestBlockEntity.class);
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.STONE, 64));
        chest.setItem(0, new ItemStack(Items.OAK_LOG, 63));
        var task = plan(s, deposit(observe(h, s, "chest"), "minecraft:oak_log", 3));
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.FAILED, "No partial failure");
            var receipt = task.actions(s.maid()).getFirst();
            h.assertTrue(receipt.reason().equals("chest_full") && receipt.progress().get("delivered") == 1 && receipt.item().equals("minecraft:oak_log"), "Bad receipt");
            h.assertTrue(chest.countItem(Items.OAK_LOG) == 64 && s.maid().getInventory().countMatching(i -> i.is(Items.OAK_LOG)) == 3, "Items lost/duplicated");
        });
    }
    @GameTest(structure = AREA, maxTicks = 600)
    public void equipObservedTreeChopAndPickupAreSeparateVerifiedActions(GameTestHelper h) {
        var s = scene(h); tree(h); s.maid().getInventory().setItem(2, new ItemStack(Items.IRON_AXE));
        var equip = step("ai-maid:equip_tool"); equip.addProperty("slot", 2); equip.addProperty("item", "minecraft:iron_axe");
        var chop = step("ai-maid-lumber:chop_tree"); chop.addProperty("target_id", observe(h, s, "tree"));
        var task = plan(s, equip, chop);
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.SUCCEEDED, "Tree sequence incomplete: " + task.actions(s.maid()));
            var receipt = task.actions(s.maid()).getLast();
            h.assertTrue(receipt.progress().get("broken") == 3 && receipt.progress().get("collected") == 3, "Broken is not collected");
            h.assertTrue(s.maid().getInventory().countMatching(i -> i.is(Items.OAK_LOG)) == 3 && h.getBlockState(TREE).isAir(), "No physical logging");
            h.assertTrue(s.maid().getMainHandItem().getDamageValue() == 3 && task.actions(s.maid()).getFirst().slot() == 2, "Tool durability or receipt incorrect");
        });
    }
    @GameTest(structure = AREA, maxTicks = 150)
    public void changedToolSlotFailsWithoutSwappingAnotherItem(GameTestHelper h) {
        var s = scene(h); s.maid().getInventory().setItem(2, new ItemStack(Items.IRON_AXE));
        var equip = step("ai-maid:equip_tool"); equip.addProperty("slot", 2); equip.addProperty("item", "minecraft:iron_axe");
        var task = plan(s, equip); s.maid().getInventory().setItem(2, new ItemStack(Items.DIAMOND));
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.FAILED && task.snapshot(s.maid()).reason().equals("inventory_changed"), "Stale slot accepted");
            h.assertTrue(s.maid().getMainHandItem().isEmpty() && s.maid().getInventory().getItem(2).is(Items.DIAMOND), "Wrong equipment transfer");
        });
    }
    @GameTest(structure = AREA, maxTicks = 250)
    public void moveUsesObservedPositionAndActuallyArrives(GameTestHelper h) {
        var s = scene(h); var pos = h.absolutePos(new BlockPos(26, 1, 20));
        var args = new JsonObject(); args.addProperty("x", pos.getX()); args.addProperty("y", pos.getY()); args.addProperty("z", pos.getZ());
        var observed = MaidTools.execute("observe_position", args, s.maid(), s.owner(), 32);
        var move = step("ai-maid:move_to"); move.add("target_id", observed.get("target_id")); var task = plan(s, move);
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.SUCCEEDED, "Move not complete");
            h.assertTrue(pos.distToCenterSqr(s.maid().position()) <= 9, "Acceptance mistaken for arrival");
        });
    }
    @GameTest(structure = AREA)
    public void wrongTargetKindIsRejectedBeforeSelectingWork(GameTestHelper h) {
        var s = scene(h); long before = s.maid().getMaidCommandRevision();
        var harvest = step("ai-maid-farming:harvest_crops"); harvest.addProperty("item", "minecraft:carrot"); harvest.addProperty("count", 1);
        harvest.addProperty("target_id", observe(h, s, "chest"));
        var args = new JsonObject(); args.addProperty("request_id", "wrong-kind"); var steps = new JsonArray(); steps.add(harvest); args.add("steps", steps);
        h.assertTrue(MaidTools.execute("submit_plan", args, s.maid(), s.owner(), 32).has("error") && s.maid().getMaidCommandRevision() == before, "Chest became harvest area"); h.succeed();
    }
    @GameTest(structure = AREA, maxTicks = 150)
    public void goalWaitsForDecisionAndRejectsCompletionWithoutEvidence(GameTestHelper h) {
        var s = scene(h); var goal = goal(s, MaidGoalTask.Limits.DEFAULT);
        try { goal.conclude(s.maid(), true, "done"); throw new AssertionError("Empty evidence accepted"); } catch (IllegalArgumentException expected) {}
        s.maid().setNoAi(false);
        h.runAfterDelay(15, () -> {
            h.assertTrue(goal.deciding() && goal.actions(s.maid()).isEmpty() && goal.active(), "Idle goal did unsolicited work");
            var child = goal.submit(s.maid(), "return", new MaidActions.Request(MaidActions.RETURN_TO_OWNER, 0, null));
            h.succeedWhen(() -> {
                h.assertTrue(child.snapshot(s.maid()).state() == MaidTask.State.SUCCEEDED && goal.completedActions() == 1, "No deferred receipt");
                h.assertTrue(goal.active() && goal.deciding(), "Goal auto-finished after one action");
                goal.conclude(s.maid(), true, "Returned as requested"); h.assertTrue(goal.snapshot(s.maid()).state() == MaidTask.State.SUCCEEDED, "No conclusion");
            });
        });
    }
    @GameTest(structure = AREA, maxTicks = 150)
    public void goalBudgetAndIdempotencyPreventExtraExecution(GameTestHelper h) {
        var s = scene(h); var goal = goal(s, new MaidGoalTask.Limits(1, 3, 1000, 32));
        var request = new MaidActions.Request(MaidActions.RETURN_TO_OWNER, 0, null); var child = goal.submit(s.maid(), "same", request); s.maid().setNoAi(false);
        h.succeedWhen(() -> {
            h.assertTrue(goal.completedActions() == 1, "Waiting");
            h.assertTrue(goal.submit(s.maid(), "same", request) == child, "Retry replayed");
            try { goal.submit(s.maid(), "new", request); throw new AssertionError("Budget exceeded"); } catch (IllegalStateException expected) {}
            goal.conclude(s.maid(), true, "done");
        });
    }
    @GameTest(structure = AREA, maxTicks = 150)
    public void failureBudgetTerminatesRepeatedMissingTools(GameTestHelper h) {
        var s = scene(h); var goal = goal(s, new MaidGoalTask.Limits(4, 1, 1000, 32));
        var equip = new MaidActions.Request(MaidActions.EQUIP_TOOL, 0, null, Identifier.parse("minecraft:iron_axe"), 0, null);
        goal.submit(s.maid(), "missing", equip); s.maid().setNoAi(false);
        h.succeedWhen(() -> h.assertTrue(goal.snapshot(s.maid()).state() == MaidTask.State.FAILED && goal.snapshot(s.maid()).reason().equals("failure_budget")
                && goal.actions(s.maid()).getFirst().reason().equals("inventory_changed"), "Budget lost action reason"));
    }
    @GameTest(structure = AREA, maxTicks = 200)
    public void goalTimeoutIsIndependentOfAnyHttpRequest(GameTestHelper h) {
        var s = scene(h); var goal = goal(s, new MaidGoalTask.Limits(4, 2, 100, 32)); s.maid().setNoAi(false);
        h.succeedWhen(() -> h.assertTrue(goal.snapshot(s.maid()).state() == MaidTask.State.FAILED && goal.snapshot(s.maid()).reason().equals("goal_timeout"), "No game-owned timeout"));
    }
    @GameTest(structure = AREA)
    public void pausedGoalCannotAcceptActionAndManualOverrideCancels(GameTestHelper h) {
        var s = scene(h); var goal = goal(s, MaidGoalTask.Limits.DEFAULT); goal.pause(s.maid());
        try { goal.submit(s.maid(), "paused", new MaidActions.Request(MaidActions.RETURN_TO_OWNER, 0, null)); throw new AssertionError("Worked in combat"); } catch (IllegalStateException expected) {}
        goal.resume(s.maid()); h.assertTrue(goal.deciding(), "No resume");
        s.maid().selectMaidCommand(MaidCommands.STAY);
        h.assertTrue(goal.snapshot(s.maid()).state() == MaidTask.State.CANCELLED, "Manual override did not cancel goal"); h.succeed();
    }
    @GameTest(structure = AREA)
    public void removingMaidCancelsGoalAndRejectsFutureAction(GameTestHelper h) {
        var s = scene(h); var goal = goal(s, MaidGoalTask.Limits.DEFAULT); s.maid().discard();
        h.assertTrue(goal.snapshot(s.maid()).state() == MaidTask.State.CANCELLED, "Removal left goal running");
        try { goal.submit(s.maid(), "late", new MaidActions.Request(MaidActions.RETURN_TO_OWNER, 0, null)); throw new AssertionError("Revived removed maid"); } catch (IllegalStateException expected) {}
        h.succeed();
    }
    @GameTest(structure = AREA, maxTicks = 250)
    public void changedTreeIsNotSilentlyReplacedWithAnotherTarget(GameTestHelper h) {
        var s = scene(h); tree(h); s.maid().setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        var chop = step("ai-maid-lumber:chop_tree"); chop.addProperty("target_id", observe(h, s, "tree")); var task = plan(s, chop);
        for (int y = 0; y < 3; y++) h.setBlock(TREE.above(y), Blocks.STONE);
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.FAILED, "Changed tree not rejected");
            h.assertTrue(task.actions(s.maid()).getFirst().progress().get("broken") == 0 && h.getBlockState(TREE).is(Blocks.STONE), "Changed world destroyed");
        });
    }
    @GameTest(structure = AREA, maxTicks = 200)
    public void fullInventoryStopsChoppingBeforeBreakingLogs(GameTestHelper h) {
        var s = scene(h); tree(h); s.maid().setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        for (int i = 0; i < s.maid().getInventory().getContainerSize(); i++) s.maid().getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        var chop = step("ai-maid-lumber:chop_tree"); chop.addProperty("target_id", observe(h, s, "tree")); var task = plan(s, chop);
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.FAILED && task.snapshot(s.maid()).reason().equals("inventory_full"), "No capacity failure");
            h.assertTrue(task.actions(s.maid()).getFirst().progress().get("broken") == 0 && h.getBlockState(TREE).is(Blocks.OAK_LOG), "Full bag destroyed logs");
        });
    }
    @GameTest(structure = AREA, maxTicks = 450)
    public void goalPausesForCombatAndResumesTheSameAction(GameTestHelper h) {
        var s = scene(h); s.maid().getInventory().setItem(0, new ItemStack(Items.WHEAT, 2));
        var request = parse(s, deposit(observe(h, s, "chest"), "minecraft:wheat", 2)); var goal = goal(s, MaidGoalTask.Limits.DEFAULT);
        goal.submit(s.maid(), "deposit", request); s.maid().setNoAi(false);
        var enemy = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 16.5F, 1, 19.5F); enemy.setNoAi(true);
        enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100); enemy.setHealth(100);
        boolean[] paused = {false}; h.runAfterDelay(5, () -> h.hurt(s.maid(), h.getLevel().damageSources().mobAttack(enemy), 1));
        h.onEachTick(() -> {
            if (!paused[0] && goal.snapshot(s.maid()).state() == MaidTask.State.PAUSED) {
                paused[0] = true; int elapsed = goal.snapshot(s.maid()).progress().get("active_ticks");
                h.runAfterDelay(12, () -> {
                    h.assertTrue(goal.snapshot(s.maid()).progress().get("active_ticks") == elapsed
                            && h.getBlockEntity(CHEST, ChestBlockEntity.class).countItem(Items.WHEAT) == 0, "Worked or consumed budget in combat"); enemy.discard();
                });
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(paused[0] && goal.completedActions() == 1 && goal.deciding(), "Goal failed to resume");
            h.assertTrue(h.getBlockEntity(CHEST, ChestBlockEntity.class).countItem(Items.WHEAT) == 2 && goal.actions(s.maid()).size() == 1, "Resume duplicated effects");
            goal.conclude(s.maid(), true, "Stored");
        });
    }
}

package io.github.ozozorz.aimaid.integration;

import java.util.*;
import com.google.gson.*;
import io.github.ozozorz.aimaid.entity.*;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.task.*;
import io.github.ozozorz.aimaid.llm.tool.MaidTools;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Exercise model-authored combinations against real Brain ticks, inventories and multiple destinations. */
public class ActionPlanGameTests {
    private static final String AREA = "ai-maid-integration-tests:delivery";
    private static final BlockPos A = new BlockPos(23, 1, 22), B = new BlockPos(23, 1, 25);
    private record Scene(AiMaidEntity maid, ServerPlayer owner, String a, String b) {}
    private Scene scene(GameTestHelper h, int initial, int crops) {
        for (int x = 15; x <= 27; x++) for (int z = 15; z <= 27; z++) h.setBlock(x, 0, z, Blocks.DIRT);
        var maid = h.spawn(ModEntityTypes.AI_MAID, 18.5F, 1, 18.5F); maid.setNoAi(true);
        var owner = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(17.5, 1, 17.5)));
        maid.setTame(true, true); maid.setOwner(owner); maid.selectMaidCommand(MaidCommands.STAY);
        if (initial > 0) maid.getInventory().setItem(0, new ItemStack(Items.WHEAT, initial));
        maid.getInventory().setItem(1, new ItemStack(Items.WHEAT_SEEDS, 16));
        for (int i = 0; i < crops; i++) {
            var pos = new BlockPos(19 + i % 4, 1, 20 + i / 4);
            h.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
            h.setBlock(pos, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        }
        h.setBlock(A, Blocks.CHEST); h.setBlock(B, Blocks.CHEST);
        return new Scene(maid, owner, observe(h, maid, owner, A), observe(h, maid, owner, B));
    }
    private String observe(GameTestHelper h, AiMaidEntity maid, ServerPlayer owner, BlockPos relative) {
        var pos = h.absolutePos(relative); var args = new JsonObject();
        args.addProperty("x", pos.getX()); args.addProperty("y", pos.getY()); args.addProperty("z", pos.getZ());
        var result = MaidTools.execute("observe_chest", args, maid, owner, 32);
        h.assertTrue(result.has("target_id"), "Observation failed: " + result); return result.get("target_id").getAsString();
    }
    private JsonObject collect(int count) { var step = step("ai-maid-farming:collect_wheat"); step.addProperty("count", count); return step; }
    private JsonObject deposit(String token, int count) { var step = step("ai-maid:deposit_wheat"); step.addProperty("count", count); step.addProperty("target_id", token); return step; }
    private JsonObject back() { return step("ai-maid:return_to_owner"); }
    private JsonObject step(String action) { var result = new JsonObject(); result.addProperty("action", action); return result; }
    private JsonObject plan(String id, JsonObject... steps) {
        var args = new JsonObject(); args.addProperty("request_id", id); var array = new JsonArray();
        for (var step : steps) array.add(step); args.add("steps", array); return args;
    }
    private JsonObject submit(Scene s, JsonObject args) { return MaidTools.execute("submit_plan", args, s.maid(), s.owner(), 32); }
    private MaidPlanTask start(GameTestHelper h, Scene s, JsonObject args) {
        var result = submit(s, args);
        h.assertTrue(result.has("task_started") && result.get("task_started").getAsBoolean()
                && !result.get("work_completed").getAsBoolean(), "Not accepted as running: " + result);
        s.maid().setNoAi(false); return (MaidPlanTask) MaidTasks.current(s.maid());
    }
    private int held(Scene s) { return s.maid().getInventory().countMatching(i -> i.is(Items.WHEAT)); }
    private int stored(GameTestHelper h, BlockPos pos) { return h.getBlockEntity(pos, ChestBlockEntity.class).countItem(Items.WHEAT); }

    @GameTest(structure = AREA, maxTicks = 250)
    public void collectOnlyLeavesWheatInInventory(GameTestHelper h) {
        var s = scene(h, 1, 4); var task = start(h, s, plan("collect-only", collect(3)));
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.SUCCEEDED, "Collection not complete");
            h.assertTrue(held(s) == 3 && stored(h, A) == 0 && stored(h, B) == 0, "Implicit deposit changed world");
            h.assertTrue(task.actions(s.maid()).size() == 1 && task.snapshot(s.maid()).progress().get("harvested") == 2, "Did not use stock first");
        });
    }
    @GameTest(structure = AREA, maxTicks = 300)
    public void depositOnlyDoesNotHarvestOrReturn(GameTestHelper h) {
        var s = scene(h, 6, 2); var task = start(h, s, plan("deposit-only", deposit(s.a(), 4)));
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.SUCCEEDED, "Deposit not complete");
            h.assertTrue(stored(h, A) == 4 && held(s) == 2 && s.maid().distanceToSqr(s.owner()) > 9, "Unexpected implicit step");
            h.assertTrue(task.snapshot(s.maid()).progress().get("harvested") == 0, "Unrequested harvest");
        });
    }
    @GameTest(structure = AREA, maxTicks = 650)
    public void splitDeliveryUsesTwoObservedChestsAndFrozenReceipts(GameTestHelper h) {
        var s = scene(h, 1, 5); var task = start(h, s, plan("split", collect(5), deposit(s.a(), 2), deposit(s.b(), 3), back()));
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.SUCCEEDED, "Split plan incomplete: " + task.snapshot(s.maid()));
            h.assertTrue(stored(h, A) == 2 && stored(h, B) == 3 && held(s) == 0 && s.maid().distanceToSqr(s.owner()) <= 9, "Split effects incorrect");
            var steps = task.actions(s.maid());
            h.assertTrue(steps.stream().allMatch(r -> r.state().equals("succeeded")) && steps.size() == 4, "Bad step ledger");
            h.assertTrue(steps.get(0).progress().get("available") == 5 && steps.get(1).progress().get("delivered") == 2
                    && steps.get(2).progress().get("delivered") == 3, "Historical receipts changed with current inventory");
        });
    }
    @GameTest(structure = AREA, maxTicks = 150)
    public void depositWithoutEnoughStockFailsWithoutImplicitHarvest(GameTestHelper h) {
        var s = scene(h, 1, 3); var task = start(h, s, plan("missing-stock", deposit(s.a(), 2), back()));
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.FAILED
                    && task.snapshot(s.maid()).reason().equals("inventory_changed"), "Missing stock did not fail");
            h.assertTrue(held(s) == 1 && stored(h, A) == 0 && task.actions(s.maid()).get(1).state().equals("skipped"), "Implicit harvest or return");
            h.assertTrue(((CropBlock) Blocks.WHEAT).isMaxAge(h.getBlockState(new BlockPos(19, 1, 20))), "Unexpected harvest");
        });
    }
    @GameTest(structure = AREA, maxTicks = 350)
    public void partialMiddleFailureSkipsLaterDepositAndReturn(GameTestHelper h) {
        var s = scene(h, 5, 0); var chest = h.getBlockEntity(A, ChestBlockEntity.class);
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.STONE, 64));
        chest.setItem(0, new ItemStack(Items.WHEAT, 63));
        var task = start(h, s, plan("partial", collect(5), deposit(s.a(), 3), deposit(s.b(), 2), back()));
        h.succeedWhen(() -> {
            var data = task.snapshot(s.maid()); var steps = task.actions(s.maid());
            h.assertTrue(data.state() == MaidTask.State.FAILED && data.reason().equals("chest_full"), "No full chest failure");
            for (int i = 0; i < 5; i++) task.tick(s.maid(), h.getLevel().getGameTime());
            h.assertTrue(stored(h, A) == 64 && stored(h, B) == 0 && held(s) == 4, "Partial effects lost or replayed");
            h.assertTrue(steps.get(1).progress().get("delivered") == 1 && steps.get(2).state().equals("skipped")
                    && steps.get(3).state().equals("skipped"), "Did not stop remaining steps");
        });
    }
    @GameTest(structure = AREA)
    public void wholePlanIsValidatedBeforeAnyWorkStarts(GameTestHelper h) {
        var s = scene(h, 5, 0); long revision = s.maid().getMaidCommandRevision();
        var extra = back(); extra.addProperty("command", "op anything");
        var wrongCount = collect(3); wrongCount.addProperty("count", 2.5);
        var nullCount = collect(3); nullCount.add("count", JsonNull.INSTANCE);
        var many = plan("many", back()); for (int i = 0; i < 8; i++) many.getAsJsonArray("steps").add(back());
        for (var request : List.of(plan("unknown", back(), step("minecraft:run_command")), plan("zero", back(), collect(0)),
                plan("big", back(), collect(65)), plan("fraction", back(), wrongCount), plan("null", back(), nullCount),
                plan("token", back(), deposit("invented", 1)), plan("extra", back(), extra), plan("empty"), many)) {
            h.assertTrue(submit(s, request).has("error"), "Invalid plan accepted: " + request);
            h.assertTrue(s.maid().getMaidCommandRevision() == revision && MaidTasks.current(s.maid()) == null && held(s) == 5,
                    "Valid prefix of invalid plan changed work");
        }
        h.succeed();
    }
    @GameTest(structure = AREA, maxTicks = 300)
    public void completedSubmissionIsNotReplayedAndIdConflictsAreRejected(GameTestHelper h) {
        var s = scene(h, 6, 0); var request = plan("once", deposit(s.a(), 2)); var task = start(h, s, request);
        long revision = s.maid().getMaidCommandRevision();
        h.assertTrue(submit(s, request).get("already_submitted").getAsBoolean(), "Active duplicate not reused");
        h.assertTrue(submit(s, plan("once", deposit(s.a(), 3))).has("error"), "Request ID conflict accepted");
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.SUCCEEDED, "Not complete");
            var replay = submit(s, request);
            h.assertTrue(replay.get("already_submitted").getAsBoolean() && replay.get("work_completed").getAsBoolean(), "Completed receipt missing");
            h.assertTrue(s.maid().getMaidCommandRevision() == revision && stored(h, A) == 2 && held(s) == 4, "Replay performed another deposit");
        });
    }
    @GameTest(structure = AREA, maxTicks = 400)
    public void cancellationKeepsCompletedStepsAndSkipsTheRest(GameTestHelper h) {
        var s = scene(h, 5, 0); var task = start(h, s, plan("cancel", deposit(s.a(), 2), deposit(s.b(), 3), back()));
        boolean[] cancelled = {false};
        h.onEachTick(() -> {
            if (!cancelled[0] && task.snapshot(s.maid()).progress().get("steps_completed") == 1) {
                cancelled[0] = true; s.owner().setPos(s.owner().position().add(40, 0, 0));
                MaidTools.execute("cancel_task", new JsonObject(), s.maid(), s.owner(), 32);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.CANCELLED, "Remote cancellation failed");
            h.assertTrue(stored(h, A) == 2 && stored(h, B) == 0 && held(s) == 3, "Cancellation lost committed effects");
            h.assertTrue(task.actions(s.maid()).get(0).state().equals("succeeded") && task.actions(s.maid()).get(2).state().equals("skipped"), "Wrong cancelled ledger");
        });
    }
    @GameTest(structure = AREA)
    public void invalidatedChestAndForeignTargetsCannotStartPlan(GameTestHelper h) {
        var s = scene(h, 3, 0);
        var stranger = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL); stranger.setPos(s.maid().position());
        h.assertTrue(MaidTools.execute("submit_plan", plan("foreign", deposit(s.a(), 2)), s.maid(), stranger, 32).has("error"), "Stranger used owner target");
        var other = h.spawn(ModEntityTypes.AI_MAID, 18.5F, 1, 19.5F); other.setNoAi(true); other.setTame(true, true); other.setOwner(s.owner());
        h.assertTrue(MaidTools.execute("submit_plan", plan("other", deposit(s.a(), 2)), other, s.owner(), 32).has("error"), "Token leaked across maids");
        h.setBlock(A, Blocks.AIR); h.setBlock(A, Blocks.CHEST);
        h.assertTrue(submit(s, plan("replaced", back(), deposit(s.a(), 2))).has("error") && MaidTasks.current(s.maid()) == null, "Replaced chest accepted");
        h.succeed();
    }
    @GameTest(structure = AREA, maxTicks = 1400)
    public void expiredObservationCannotAuthorizeAPlan(GameTestHelper h) {
        var s = scene(h, 2, 0);
        h.runAfterDelay(1202, () -> {
            h.assertTrue(submit(s, plan("expired", deposit(s.a(), 2))).has("error") && MaidTasks.current(s.maid()) == null, "Expired target accepted"); h.succeed();
        });
    }
    @GameTest(structure = AREA, maxTicks = 350)
    public void replacingSecondChestAfterStartStopsBeforeUsingIt(GameTestHelper h) {
        var s = scene(h, 4, 0); var task = start(h, s, plan("changed-later", deposit(s.a(), 2), deposit(s.b(), 2), back()));
        h.runAfterDelay(2, () -> { h.setBlock(B, Blocks.AIR); h.setBlock(B, Blocks.CHEST); });
        h.succeedWhen(() -> {
            h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.FAILED && task.snapshot(s.maid()).reason().equals("chest_changed"), "Changed target ignored");
            h.assertTrue(stored(h, A) == 2 && stored(h, B) == 0 && held(s) == 2 && task.actions(s.maid()).get(2).state().equals("skipped"), "Wrong partial result");
        });
    }
    @GameTest(structure = AREA)
    public void legacyPresetCannotReplaceActivePlanAndManualChangeCancels(GameTestHelper h) {
        var s = scene(h, 3, 0); var task = start(h, s, plan("busy", deposit(s.a(), 2), back())); s.maid().setNoAi(true);
        h.assertTrue(submit(s, plan("other-plan", back())).has("error"), "Active plan replaced");
        var pos = h.absolutePos(A);
        try {
            int selected = h.getLevel().getServer().getCommands().getDispatcher().execute("maid uuid " + s.maid().getUUID()
                    + " command ai-maid-farming:deliver_wheat 2 " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), s.owner().createCommandSourceStack().withSuppressedOutput());
            h.assertTrue(selected == 0 && MaidTasks.current(s.maid()) == task, "Legacy preset replaced plan");
        } catch (Exception e) { throw new AssertionError(e); }
        s.maid().selectMaidCommand(MaidCommands.STAY);
        h.assertTrue(task.snapshot(s.maid()).state() == MaidTask.State.CANCELLED && held(s) == 3, "Manual override failed"); h.succeed();
    }
    @GameTest(structure = AREA, maxTicks = 650)
    public void planPausesForRealCombatThenResumesItsCurrentStep(GameTestHelper h) {
        var s = scene(h, 0, 3); var task = start(h, s, plan("combat", collect(2), deposit(s.a(), 2), back()));
        var enemy = h.spawn(EntityTypes.ZOMBIE, 16.5F, 1, 19.5F); enemy.setNoAi(true);
        enemy.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); enemy.setHealth(100);
        boolean[] paused = {false};
        h.runAfterDelay(5, () -> h.hurt(s.maid(), h.getLevel().damageSources().mobAttack(enemy), 1));
        h.onEachTick(() -> {
            if (!paused[0] && task.snapshot(s.maid()).state() == MaidTask.State.PAUSED) {
                paused[0] = true; int before = held(s);
                h.runAfterDelay(12, () -> { h.assertTrue(held(s) == before, "Plan worked during combat"); enemy.discard(); });
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(paused[0] && task.snapshot(s.maid()).state() == MaidTask.State.SUCCEEDED, "No combat resume");
            h.assertTrue(stored(h, A) == 2 && held(s) == 0, "Combat replayed effects");
        });
    }
}

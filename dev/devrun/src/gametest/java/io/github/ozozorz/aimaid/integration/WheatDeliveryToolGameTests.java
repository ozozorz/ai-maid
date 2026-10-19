package io.github.ozozorz.aimaid.integration;

import com.google.gson.JsonObject;
import io.github.ozozorz.aimaid.entity.*;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.task.MaidTasks;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import io.github.ozozorz.aimaid.llm.tool.MaidTools;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

public class WheatDeliveryToolGameTests {
    private static final String AREA = "ai-maid-integration-tests:delivery";
    private static final BlockPos CHEST = new BlockPos(23, 1, 23);
    private record Scene(AiMaidEntity maid, ServerPlayer owner) {}
    private Scene scene(GameTestHelper h) {
        for (int x = 15; x <= 26; x++) for (int z = 15; z <= 26; z++) h.setBlock(x, 0, z, Blocks.DIRT);
        var maid = h.spawn(ModEntityTypes.AI_MAID, 18.5F, 1, 18.5F); maid.setNoAi(true);
        var owner = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL); owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(17.5, 1, 17.5)));
        maid.setTame(true, true); maid.setOwner(owner); maid.selectMaidCommand(MaidCommands.STAY);
        h.setBlock(CHEST, Blocks.CHEST);
        return new Scene(maid, owner);
    }
    private JsonObject observe(GameTestHelper h, Scene s) {
        var pos = h.absolutePos(CHEST); var args = new JsonObject();
        args.addProperty("x", pos.getX()); args.addProperty("y", pos.getY()); args.addProperty("z", pos.getZ());
        var result = MaidTools.execute("observe_wheat_delivery", args, s.maid(), s.owner(), 32);
        h.assertTrue(result.has("target_id"), "Observation failed: " + result); return result;
    }
    private JsonObject request(String target, int count) {
        var args = new JsonObject(); args.addProperty("target_id", target); args.addProperty("count", count); return args;
    }
    private JsonObject start(GameTestHelper h, Scene s, int count) {
        var target = observe(h, s).get("target_id").getAsString();
        var result = MaidTools.execute("start_wheat_delivery", request(target, count), s.maid(), s.owner(), 32);
        h.assertTrue(result.has("task_started"), "Task rejected: " + result); return result;
    }

    @GameTest(structure = AREA)
    public void toolStartsFiniteTaskAndDuplicateDoesNotRestart(GameTestHelper h) {
        var s = scene(h); var observation = observe(h, s);
        var args = request(observation.get("target_id").getAsString(), 4);
        var first = MaidTools.execute("start_wheat_delivery", args, s.maid(), s.owner(), 32);
        h.assertTrue(first.has("task_started") && !first.get("work_completed").getAsBoolean(), "Acceptance confused with completion: " + first);
        long revision = s.maid().getMaidCommandRevision();
        var second = MaidTools.execute("start_wheat_delivery", args, s.maid(), s.owner(), 32);
        h.assertTrue(first.getAsJsonObject("task").get("id").equals(second.getAsJsonObject("task").get("id"))
                && s.maid().getMaidCommandRevision() == revision, "Duplicate restarted task");
        h.succeed();
    }

    @GameTest(structure = AREA, maxTicks = 500)
    public void modelToolCompletesHarvestDepositAndReturn(GameTestHelper h) {
        var s = scene(h); s.maid().getInventory().setItem(0, new ItemStack(Items.WHEAT, 1));
        s.maid().getInventory().setItem(1, new ItemStack(Items.WHEAT_SEEDS, 4));
        for (int x = 19; x <= 20; x++) {
            h.setBlock(x, 0, 20, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
            h.setBlock(new BlockPos(x, 1, 20), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        }
        start(h, s, 3); s.maid().setNoAi(false);
        h.succeedWhen(() -> {
            var task = MaidTools.snapshot(s.maid()).getAsJsonObject("task");
            h.assertTrue(task.get("state").getAsString().equals("succeeded"), "Task not complete: " + task);
            var chest = h.getBlockEntity(CHEST, ChestBlockEntity.class);
            int count = 0; for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.WHEAT)) count += chest.getItem(i).getCount();
            h.assertTrue(count == 3 && task.getAsJsonObject("progress").get("delivered").getAsInt() == 3, "Result disagrees with world");
            h.assertTrue(s.maid().distanceToSqr(s.owner()) <= 9, "Not physically returned");
        });
    }

    @GameTest(structure = AREA)
    public void fabricatedTargetsAndInvalidCountsDoNotChangeWork(GameTestHelper h) {
        var s = scene(h); var target = observe(h, s).get("target_id").getAsString(); long revision = s.maid().getMaidCommandRevision();
        for (var args : new JsonObject[]{request("invented", 3), request(target, 0), request(target, 65),
                ChatProtocol.object("{\"count\":2.5,\"target_id\":\"" + target + "\"}"),
                ChatProtocol.object("{\"count\":\"3\",\"target_id\":\"" + target + "\"}")})
            h.assertTrue(MaidTools.execute("start_wheat_delivery", args, s.maid(), s.owner(), 32).has("error"), "Invalid request accepted");
        h.assertTrue(s.maid().getMaidCommandRevision() == revision && MaidTasks.current(s.maid()) == null, "Invalid request changed work"); h.succeed();
    }

    @GameTest(structure = AREA)
    public void observedChestReplacementInvalidatesTarget(GameTestHelper h) {
        var s = scene(h); var target = observe(h, s).get("target_id").getAsString();
        h.setBlock(CHEST, Blocks.AIR); h.setBlock(CHEST, Blocks.CHEST);
        var result = MaidTools.execute("start_wheat_delivery", request(target, 3), s.maid(), s.owner(), 32);
        h.assertTrue(result.has("error") && MaidTasks.current(s.maid()) == null, "Replaced chest accepted"); h.succeed();
    }

    @GameTest(structure = AREA)
    public void strangerCannotUseOwnersTargetOrCancelTask(GameTestHelper h) {
        var s = scene(h); var target = observe(h, s).get("target_id").getAsString();
        var stranger = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL); stranger.setPos(s.maid().position());
        h.assertTrue(MaidTools.execute("start_wheat_delivery", request(target, 3), s.maid(), stranger, 32).has("error"), "Stranger started task");
        start(h, s, 3);
        h.assertTrue(MaidTools.execute("cancel_task", new JsonObject(), s.maid(), stranger, 32).has("error"), "Stranger cancelled task");
        h.assertTrue(!MaidTasks.current(s.maid()).snapshot(s.maid()).state().terminal(), "Unauthorized cancellation changed task"); h.succeed();
    }

    @GameTest(structure = AREA, maxTicks = 100)
    public void cancellationStopsTaskAndRetainsRealInventory(GameTestHelper h) {
        var s = scene(h); s.maid().getInventory().setItem(0, new ItemStack(Items.WHEAT, 3)); start(h, s, 3);
        s.owner().setPos(s.maid().position().add(40, 0, 0));
        var result = MaidTools.execute("cancel_task", new JsonObject(), s.maid(), s.owner(), 32); s.maid().setNoAi(false);
        h.assertTrue(result.get("task_cancelled").getAsBoolean(), "Cancel not acknowledged");
        h.runAfterDelay(50, () -> {
            h.assertTrue(MaidTools.snapshot(s.maid()).getAsJsonObject("task").get("state").getAsString().equals("cancelled"), "Lost cancellation result");
            h.assertTrue(s.maid().getMaidCommand() == MaidCommands.STAY && s.maid().getInventory().countMatching(i -> i.is(Items.WHEAT)) == 3,
                    "Cancelled task changed work or cargo"); h.succeed();
        });
    }

    @GameTest(structure = AREA)
    public void pointedChestCommandStartsWithoutCoordinates(GameTestHelper h) {
        var s = scene(h);
        s.owner().setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(23.5, 1, 20.5)));
        var offset = net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(CHEST)).subtract(s.owner().getEyePosition());
        s.owner().setYRot((float) Math.toDegrees(Math.atan2(-offset.x, offset.z)));
        s.owner().setXRot((float) -Math.toDegrees(Math.atan2(offset.y, Math.sqrt(offset.x * offset.x + offset.z * offset.z))));
        try {
            int accepted = h.getLevel().getServer().getCommands().getDispatcher().execute(
                    "maid uuid " + s.maid().getUUID() + " command ai-maid-farming:deliver_wheat 3",
                    s.owner().createCommandSourceStack().withSuppressedOutput());
            h.assertTrue(accepted == 1 && MaidTasks.current(s.maid()).snapshot(s.maid()).destination().pos().equals(h.absolutePos(CHEST)), "Pointed chest not selected");
        } catch (Exception e) { throw new AssertionError(e); }
        h.succeed();
    }
}

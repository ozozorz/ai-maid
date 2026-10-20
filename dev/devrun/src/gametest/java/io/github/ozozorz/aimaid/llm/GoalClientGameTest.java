package io.github.ozozorz.aimaid.llm;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.task.*;
import io.github.ozozorz.aimaid.llm.client.MaidChatScreen;
import io.github.ozozorz.aimaid.llm.config.LlmConfig;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Mock only the model: use real HTTP, packets, Brain ticks, world transfers and chat UI. */
public final class GoalClientGameTest implements FabricClientGameTest {
    private record Scene(UUID maid, BlockPos base) {}
    @Override public void runTest(ClientGameTestContext context) {
        try (var model = new Model(); var world = context.worldBuilder().create()) {
            var server = world.getServer(); var connection = world.getConnection(); connection.waitForChunksRender();
            Scene scene = server.computeOnServer(s -> {
                LlmAddon.install(s, model.config()); var owner = connection.getServerPlayer(); var level = connection.getServerLevel();
                var base = owner.blockPosition().below();
                for (int x = -3; x <= 19; x++) for (int z = -3; z <= 10; z++) {
                    level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.DIRT.defaultBlockState());
                    for (int y = 1; y <= 4; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                owner.connection.teleport(base.getX() + .5, base.getY() + 1, base.getZ() + .5, 0, 15);
                var maid = new AiMaidEntity(level); maid.setPos(base.getX() + 2.5, base.getY() + 1, base.getZ() + 2.5);
                maid.setTame(true, true); maid.setOwner(owner); maid.selectMaidCommand(MaidCommands.STAY);
                maid.getInventory().setItem(0, new ItemStack(Items.WHEAT, 1)); maid.getInventory().setItem(1, new ItemStack(Items.WHEAT_SEEDS, 8)); level.addFreshEntity(maid);
                for (int x = 3; x <= 5; x++) {
                    var crop = base.offset(x, 1, 4); level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
                    level.setBlockAndUpdate(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
                }
                model.a = base.offset(6, 1, 6); model.b = base.offset(9, 1, 6);
                level.setBlockAndUpdate(model.a, Blocks.CHEST.defaultBlockState()); level.setBlockAndUpdate(model.b, Blocks.CHEST.defaultBlockState());
                var chest = (ChestBlockEntity) level.getBlockEntity(model.a);
                for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.STONE, 64));
                chest.setItem(0, new ItemStack(Items.WHEAT, 63));
                try { s.getCommands().getDispatcher().execute("maid uuid " + maid.getUUID() + " chat", owner.createCommandSourceStack()); }
                catch (Exception e) { throw new AssertionError(e); }
                return new Scene(maid.getUUID(), base);
            });
            context.waitForScreen(MaidChatScreen.class);
            send(context, "Collect 3 wheat and put them into " + model.a.toShortString() + ". If full, use my spare chest at " + model.b.toShortString() + ", then return to me.");
            server.waitFor(s -> MaidTasks.current((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid())) instanceof MaidGoalTask);
            server.runOnServer(s -> {
                var owner = connection.getServerPlayer(); owner.connection.teleport(scene.base().getX() + 15.5, scene.base().getY() + 1, scene.base().getZ() + .5, 0, 15);
            });
            context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat && !chat.busy() && chat.messages().stream().anyMatch(m -> m.getString().contains("Adaptive goal completed")));
            server.runOnServer(s -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid()); var goal = (MaidGoalTask) MaidTasks.current(maid);
                check(goal.snapshot(maid).state() == MaidTask.State.SUCCEEDED, "Goal failed: " + goal.snapshot(maid));
                var receipts = goal.actions(maid); check(receipts.size() == 4, "Duplicate request executed another action");
                check(receipts.get(0).progress().get("available") == 3 && receipts.get(0).progress().get("harvested") == 2, "Did not harvest shortfall");
                check(receipts.get(1).reason().equals("chest_full") && receipts.get(1).progress().get("delivered") == 1
                        && receipts.get(2).progress().get("delivered") == 2, "Partial recovery lost evidence");
                check(((ChestBlockEntity) connection.getServerLevel().getBlockEntity(model.a)).countItem(Items.WHEAT) == 64
                        && ((ChestBlockEntity) connection.getServerLevel().getBlockEntity(model.b)).countItem(Items.WHEAT) == 2, "Recovery duplicated/lost items");
                check(maid.getInventory().countMatching(i -> i.is(Items.WHEAT)) == 0 && maid.distanceToSqr(connection.getServerPlayer()) <= 9, "No real return");
                check(model.requests.size() == 8 && model.error == null, "Wrong feedback round trips: " + model.error);
            });
            context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat && goalStatus(chat, "succeeded"));
            context.takeScreenshot("goal-adaptive-complete");

            // A delayed model reply must not revive a cancelled, overridden or reloaded goal.
            for (String mode : List.of("cancel", "override", "reload")) {
                model.reset(mode);
                server.runOnServer(s -> {
                    var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                    maid.getInventory().setItem(0, new ItemStack(Items.IRON_AXE)); maid.selectMaidCommand(MaidCommands.STAY);
                    LlmAddon.install(s, model.config());
                });
                send(context, "Equip the iron axe in slot zero.");
                server.waitFor(s -> model.delayed.getCount() == 0);
                MaidGoalTask cancelled = server.computeOnServer(s -> (MaidGoalTask) MaidTasks.current((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid())));
                if (mode.equals("cancel")) context.clickScreenButton("llm.ai-maid.cancel");
                else server.runOnServer(s -> {
                    if (mode.equals("override")) ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid())).selectMaidCommand(MaidCommands.STAY);
                    else LlmAddon.install(s, model.config());
                });
                server.waitFor(s -> !cancelled.active()); model.release.countDown(); context.waitTicks(15);
                server.runOnServer(s -> {
                    var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                    check(cancelled.snapshot(maid).state() == MaidTask.State.CANCELLED && cancelled.actions(maid).isEmpty(), "Late reply revived " + mode);
                    check(maid.getMainHandItem().isEmpty() && maid.getInventory().getItem(0).is(Items.IRON_AXE), "Late reply equipped tool");
                });
                context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat && !chat.busy());
            }
            // Query loops and HTTP errors terminate predictably without executing world actions.
            for (String mode : List.of("budget", "http_error")) {
                model.reset(mode); send(context, "Please help with a finite task.");
                server.waitFor(s -> model.requests.size() >= 2);
                server.waitFor(s -> {
                    var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                    return MaidTasks.current(maid).snapshot(maid).state() == MaidTask.State.FAILED;
                });
                server.runOnServer(s -> {
                    var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                    check(MaidTasks.current(maid).actions(maid).isEmpty(), "Query/error path changed world");
                    check(model.requests.size() == (mode.equals("budget") ? 6 : 2), "Unbounded model calls");
                });
                context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat && !chat.busy());
            }
            context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat && goalStatus(chat, "failed"));
            context.takeScreenshot("goal-cancelled-and-bounded");
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private static void send(ClientGameTestContext context, String text) {
        context.runOnClient(c -> ((MaidChatScreen) c.gui.screen()).setInput(text)); context.clickScreenButton("llm.ai-maid.send");
    }
    private static boolean goalStatus(MaidChatScreen chat, String state) {
        if (chat.messages().isEmpty()) return false;
        // Inspect the goal's own state, not a nested successful/failed child action receipt.
        var message = chat.messages().getLast();
        return message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().equals("status.ai-maid.goal.progress")
                && text.getArgs()[0] instanceof net.minecraft.network.chat.Component status
                && status.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                && translated.getKey().equals("status.ai-maid.plan.state." + state);
    }
    private static void check(boolean b, String text) { if (!b) throw new AssertionError(text); }
    private static JsonObject json(String text) { return ChatProtocol.object(text); }
    private static JsonObject result(JsonObject request, String id) {
        for (var message : request.getAsJsonArray("messages")) {
            var row = message.getAsJsonObject();
            if (row.has("tool_call_id") && row.get("tool_call_id").getAsString().equals(id)) return json(row.get("content").getAsString());
        }
        throw new AssertionError("Missing tool receipt: " + id);
    }
    private static JsonObject coords(BlockPos p) { var j = new JsonObject(); j.addProperty("x", p.getX()); j.addProperty("y", p.getY()); j.addProperty("z", p.getZ()); return j; }
    private static JsonObject call(String id, String name, JsonObject args) {
        var fn = new JsonObject(); fn.addProperty("name", name); fn.addProperty("arguments", args.toString());
        var call = new JsonObject(); call.addProperty("id", id); call.addProperty("type", "function"); call.add("function", fn); return call;
    }
    private static String tools(JsonObject... calls) {
        var msg = ChatProtocol.message("assistant", ""); var array = new JsonArray(); for (var c : calls) array.add(c); msg.add("tool_calls", array);
        var choice = new JsonObject(); choice.addProperty("finish_reason", "tool_calls"); choice.add("message", msg);
        var choices = new JsonArray(); choices.add(choice); var reply = new JsonObject(); reply.add("choices", choices); return reply.toString();
    }
    private static final class Model implements AutoCloseable {
        final HttpServer http; final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        final List<JsonObject> requests = new CopyOnWriteArrayList<>(); volatile BlockPos a, b;
        volatile String mode = "adaptive"; volatile Throwable error;
        volatile CountDownLatch delayed = new CountDownLatch(1), release = new CountDownLatch(1);
        JsonObject collect; String targetA, targetB;
        Model() throws Exception {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); http.setExecutor(executor);
            http.createContext("/chat", exchange -> {
                try {
                    var request = json(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); int phase = requests.size(); requests.add(request);
                    if (mode.equals("http_error") && phase > 0) { exchange.sendResponseHeaders(503, -1); return; }
                    String reply = answer(request, phase); byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
                } catch (Throwable e) { error = e; } finally { exchange.close(); }
            }); http.start();
        }
        void reset(String mode) { this.mode = mode; requests.clear(); error = null; delayed = new CountDownLatch(1); release = new CountDownLatch(1); }
        String action(String callId, String requestId, JsonObject step) {
            var args = new JsonObject(); args.addProperty("request_id", requestId); args.add("step", step); return tools(call(callId, "execute_action", args));
        }
        JsonObject deposit(String token, int count) {
            var step = json("{\"action\":\"ai-maid:deposit_items\",\"item\":\"minecraft:wheat\"}"); step.addProperty("target_id", token); step.addProperty("count", count); return step;
        }
        String answer(JsonObject request, int phase) throws Exception {
            if (phase == 0) return tools(call("start", "start_goal", new JsonObject()));
            if (mode.equals("budget")) return tools(call("query-" + phase, "query_maid", new JsonObject()));
            if (!mode.equals("adaptive")) {
                delayed.countDown(); check(release.await(8, TimeUnit.SECONDS), "No late-reply release");
                return action("late", "late", json("{\"action\":\"ai-maid:equip_tool\",\"item\":\"minecraft:iron_axe\",\"slot\":0}"));
            }
            return switch (phase) {
                case 1 -> tools(call("a", "observe_chest", coords(a)), call("b", "observe_chest", coords(b)), call("area", "observe_nearby", new JsonObject()));
                case 2 -> {
                    targetA = result(request, "a").get("target_id").getAsString(); targetB = result(request, "b").get("target_id").getAsString();
                    collect = json("{\"action\":\"ai-maid-farming:harvest_crops\",\"item\":\"minecraft:wheat\",\"count\":3}");
                    for (var v : result(request, "area").getAsJsonArray("targets")) if (v.getAsJsonObject().get("kind").getAsString().equals("area")) collect.add("target_id", v.getAsJsonObject().get("target_id"));
                    check(collect.has("target_id"), "No observed farm"); yield action("harvest", "collect-once", collect);
                }
                case 3 -> {
                    var receipt = result(request, "harvest"); check(receipt.get("ok").getAsBoolean() && receipt.getAsJsonObject("action_result").get("state").getAsString().equals("succeeded"), "Model called before harvest completed");
                    yield action("harvest-retry", "collect-once", collect);
                }
                case 4 -> { check(result(request, "harvest").equals(result(request, "harvest-retry")), "Duplicate not served exact receipt"); yield action("first", "first-chest", deposit(targetA, 3)); }
                case 5 -> {
                    var receipt = result(request, "first"); check(!receipt.get("ok").getAsBoolean(), "Expected partial failure");
                    int delivered = receipt.getAsJsonObject("action_result").getAsJsonObject("progress").get("delivered").getAsInt();
                    check(delivered == 1, "No actual partial amount"); yield action("spare", "spare-chest", deposit(targetB, 3 - delivered));
                }
                case 6 -> { check(result(request, "spare").get("ok").getAsBoolean(), "Spare deposit not complete"); yield action("return", "return", json("{\"action\":\"ai-maid:return_to_owner\"}")); }
                case 7 -> { check(result(request, "return").get("ok").getAsBoolean(), "Return not complete"); yield tools(call("finish", "finish_goal", json("{\"outcome\":\"completed\",\"summary\":\"Adaptive goal completed: 1 wheat in the first chest, 2 in the authorized spare, returned.\"}"))); }
                default -> throw new AssertionError("Unexpected model request " + phase);
            };
        }
        LlmConfig config() { return new LlmConfig("http://127.0.0.1:" + http.getAddress().getPort() + "/chat", "mock", "", "test", "max_tokens", 10, 512, 2, 0, 2, mode.equals("adaptive") ? 1 : 4, 8); }
        @Override public void close() { release.countDown(); http.stop(0); executor.shutdownNow(); }
    }
}

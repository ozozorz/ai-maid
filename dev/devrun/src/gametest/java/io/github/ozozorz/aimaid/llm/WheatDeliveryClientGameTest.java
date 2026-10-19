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

/** Real chat packets, tool round trips, task ticks and completion UI; only the model is local/mock. */
public class WheatDeliveryClientGameTest implements FabricClientGameTest {
    private record Scene(UUID maid, BlockPos chest) {}
    protected boolean plans() { return false; }

    @Override public void runTest(ClientGameTestContext context) {
        try (var model = new Model(plans()); var world = context.worldBuilder().create()) {
            var server = world.getServer(); var connection = world.getConnection(); connection.waitForChunksRender();
            Scene scene = server.computeOnServer(s -> {
                LlmAddon.install(s, model.config());
                var owner = connection.getServerPlayer(); var level = connection.getServerLevel(); var base = owner.blockPosition().below();
                for (int x = -3; x <= 9; x++) for (int z = -3; z <= 9; z++) {
                    level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.DIRT.defaultBlockState());
                    for (int y = 1; y <= 4; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                owner.connection.teleport(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 0, 15);
                var maid = new AiMaidEntity(level); maid.setPos(base.getX() + 2.5, base.getY() + 1, base.getZ() + 2.5);
                maid.setTame(true, true); maid.setOwner(owner); maid.selectMaidCommand(MaidCommands.STAY);
                maid.getInventory().setItem(0, new ItemStack(Items.WHEAT, 2));
                maid.getInventory().setItem(1, new ItemStack(Items.WHEAT_SEEDS, 8)); level.addFreshEntity(maid);
                for (int x = 3; x <= 4; x++) {
                    var crop = base.offset(x, 1, 4);
                    level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
                    level.setBlockAndUpdate(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
                }
                var chest = base.offset(6, 1, 6); level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState()); model.chest = chest;
                try { s.getCommands().getDispatcher().execute("maid uuid " + maid.getUUID() + " chat", owner.createCommandSourceStack()); }
                catch (Exception e) { throw new AssertionError(e); }
                return new Scene(maid.getUUID(), chest);
            });
            context.waitForScreen(MaidChatScreen.class);
            send(context, "Gather four wheat, " + (plans() ? "deposit two and then another two" : "put them") + " in the chest at " + scene.chest().toShortString() + ", then return.");
            context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat && !chat.busy()
                    && chat.messages().stream().anyMatch(m -> m.getString().contains("Delivery accepted")));
            server.waitFor(s -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                return MaidTasks.current(maid) != null && MaidTasks.current(maid).snapshot(maid).state().terminal();
            });
            server.runOnServer(s -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                var task = MaidTasks.current(maid).snapshot(maid);
                check(task.state() == MaidTask.State.SUCCEEDED && task.progress().get("delivered") == 4, "Wrong result: " + task);
                var chest = (ChestBlockEntity) connection.getServerLevel().getBlockEntity(scene.chest());
                check(chest.countItem(Items.WHEAT) == 4 && maid.distanceToSqr(connection.getServerPlayer()) <= 9, "No real deposit/return");
                check(model.requests.size() == 3 && model.requests.get(2).toString().contains("only_one_selection_per_turn"), "Mutation limit not applied to tasks");
                check(model.requests.get(2).toString().contains("work_completed"), "Task acceptance was not returned to model");
                if (plans()) {
                    var steps = MaidTasks.current(maid).actions(maid);
                    check(steps.size() == 4 && steps.get(1).progress().get("delivered") == 2 && steps.get(2).progress().get("delivered") == 2,
                            "LLM-selected repeated steps were not executed separately");
                    check(!model.requests.get(2).toString().contains("request_id_conflict"), "Reordered duplicate JSON was not reused");
                    int accepted = 0;
                    for (var message : model.requests.get(2).getAsJsonArray("messages")) {
                        var row = message.getAsJsonObject();
                        if (row.has("tool_call_id") && row.get("tool_call_id").getAsString().startsWith("start-")) {
                            var receipt = ChatProtocol.object(row.get("content").getAsString());
                            check(receipt.has("task_started") && receipt.get("task_started").getAsBoolean(), "Equivalent duplicate was rejected");
                            accepted++;
                        }
                    }
                    check(accepted == 2, "Missing duplicate tool receipt");
                }
            });
            context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat
                    && chat.messages().stream().anyMatch(m -> m.toString().contains(plans() ? "plan.state.succeeded" : "delivery.state.succeeded")));
            context.takeScreenshot(plans() ? "action-plan-complete" : "wheat-delivery-complete");

            // The cancel button must work after the HTTP turn ended, while a game task is still active.
            model.count = 2;
            server.runOnServer(s -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                maid.setNoAi(true); maid.getInventory().setItem(0, new ItemStack(Items.WHEAT, 2));
            });
            send(context, "Deliver two more wheat to that same chest and come back.");
            context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat && !chat.busy() && model.requests.size() == 6);
            server.runOnServer(s -> {
                var owner = connection.getServerPlayer();
                owner.connection.teleport(owner.getX() + 40, owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot());
            });
            context.clickScreenButton("llm.ai-maid.cancel");
            server.waitFor(s -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                return MaidTasks.current(maid).snapshot(maid).state() == MaidTask.State.CANCELLED;
            });
            server.runOnServer(s -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                check(maid.getMaidCommand() == MaidCommands.STAY && maid.getInventory().countMatching(i -> i.is(Items.WHEAT)) == 2,
                        "Cancellation lost cargo or failed to stop the task");
            });
            context.takeScreenshot(plans() ? "action-plan-cancelled" : "wheat-delivery-cancelled");
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private static void send(ClientGameTestContext context, String message) {
        context.runOnClient(c -> ((MaidChatScreen) c.gui.screen()).setInput(message)); context.clickScreenButton("llm.ai-maid.send");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static JsonObject call(String id, String name, JsonObject args) {
        var fn = new JsonObject(); fn.addProperty("name", name); fn.addProperty("arguments", args.toString());
        var call = new JsonObject(); call.addProperty("id", id); call.addProperty("type", "function"); call.add("function", fn); return call;
    }
    private static String response(JsonObject message) {
        var choice = new JsonObject(); choice.addProperty("finish_reason", message.has("tool_calls") ? "tool_calls" : "stop"); choice.add("message", message);
        var choices = new JsonArray(); choices.add(choice); var result = new JsonObject(); result.add("choices", choices); return result.toString();
    }
    private static String tools(JsonObject... calls) {
        var message = ChatProtocol.message("assistant", ""); var array = new JsonArray(); for (var call : calls) array.add(call);
        message.add("tool_calls", array); return response(message);
    }

    private static final class Model implements AutoCloseable {
        final HttpServer http;
        final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        final List<JsonObject> requests = new CopyOnWriteArrayList<>();
        volatile BlockPos chest;
        volatile int count = 4;
        Model(boolean plans) throws Exception {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); http.setExecutor(executor);
            http.createContext("/chat", exchange -> {
                try {
                    var request = ChatProtocol.object(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    int phase = requests.size() % 3, turn = requests.size() / 3; requests.add(request);
                    String reply;
                    if (phase == 0) {
                        var args = new JsonObject(); args.addProperty("x", chest.getX()); args.addProperty("y", chest.getY()); args.addProperty("z", chest.getZ());
                        reply = tools(call("observe-" + turn, plans ? "observe_chest" : "observe_wheat_delivery", args));
                    } else if (phase == 1) {
                        String token = null;
                        for (var item : request.getAsJsonArray("messages")) {
                            var message = item.getAsJsonObject();
                            if (message.has("tool_call_id") && message.get("tool_call_id").getAsString().equals("observe-" + turn))
                                token = ChatProtocol.object(message.get("content").getAsString()).get("target_id").getAsString();
                        }
                        check(token != null, "No observed target in model context");
                        var args = new JsonObject(); args.addProperty("count", count); args.addProperty("target_id", token);
                        if (plans) {
                            var sequence = new JsonArray();
                            if (turn == 0) sequence.add(ChatProtocol.object("{\"action\":\"ai-maid-farming:collect_wheat\",\"count\":4}"));
                            var deposit = new JsonObject(); deposit.addProperty("action", "ai-maid:deposit_wheat"); deposit.addProperty("count", 2); deposit.addProperty("target_id", token);
                            sequence.add(deposit);
                            if (turn == 0) sequence.add(deposit.deepCopy());
                            sequence.add(ChatProtocol.object("{\"action\":\"ai-maid:return_to_owner\"}"));
                            args = new JsonObject(); args.addProperty("request_id", "chat-plan-" + turn); args.add("steps", sequence);
                        }
                        var duplicate = args.deepCopy();
                        if (plans) {
                            var steps = duplicate.getAsJsonArray("steps");
                            for (int i = 0; i < steps.size(); i++) {
                                var source = steps.get(i).getAsJsonObject(); var sorted = new JsonObject();
                                source.keySet().stream().sorted(Comparator.reverseOrder()).forEach(k -> sorted.add(k, source.get(k)));
                                steps.set(i, sorted);
                            }
                        }
                        String startTool = plans ? "submit_plan" : "start_wheat_delivery";
                        reply = tools(call("start-a-" + turn, startTool, args), call("start-b-" + turn, startTool, duplicate),
                                call("forbidden-" + turn, "select_command", ChatProtocol.object("{\"command_id\":\"ai-maid:free\"}")));
                    } else reply = response(ChatProtocol.message("assistant", "Delivery accepted; waiting for the server's actual result."));
                    byte[] bytes = reply.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
                } finally { exchange.close(); }
            }); http.start();
        }
        LlmConfig config() { return new LlmConfig("http://127.0.0.1:" + http.getAddress().getPort() + "/chat", "mock", "", "test", "max_tokens", 10, 512, 2, 0, 2, 4, 32); }
        @Override public void close() { http.stop(0); executor.shutdownNow(); }
    }
}

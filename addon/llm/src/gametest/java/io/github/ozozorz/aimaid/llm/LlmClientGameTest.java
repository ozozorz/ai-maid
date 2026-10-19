package io.github.ozozorz.aimaid.llm;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.llm.client.MaidChatScreen;
import io.github.ozozorz.aimaid.llm.config.LlmConfig;
import io.github.ozozorz.aimaid.llm.network.ChatRequest;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** 真客户端、真实网络包和真实 Maid；只有外部模型替换为本机 HTTP 服务。 */
public class LlmClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var model = new MockModel(); var world = context.worldBuilder().create()) {
            var server = world.getServer(); var connection = world.getConnection(); connection.waitForChunksRender();
            UUID id = server.computeOnServer(s -> {
                LlmAddon.install(s, model.config());
                var owner = connection.getServerPlayer(); var level = connection.getServerLevel(); var base = owner.blockPosition().below();
                for (int x = -3; x <= 4; x++) for (int z = -3; z <= 4; z++) {
                    level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                    for (int y = 1; y <= 4; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                var maid = new AiMaidEntity(level); maid.setPos(owner.position().add(1, 0, 0)); maid.setNoAi(true);
                maid.setTame(true, true); maid.setOwner(owner); maid.setCustomName(Component.literal("Rem")); level.addFreshEntity(maid);
                // 经过真实命令树打开 Screen。
                try { s.getCommands().getDispatcher().execute("maid uuid " + maid.getUUID() + " chat", owner.createCommandSourceStack()); }
                catch (Exception e) { throw new AssertionError(e); }
                return maid.getUUID();
            });
            context.waitForScreen(MaidChatScreen.class);
            long initialRevision = server.computeOnServer(s -> ((AiMaidEntity)connection.getServerLevel().getEntity(id)).getMaidCommandRevision());
            model.add(toolReply(call("select-a", "select_command", "{\"command_id\":\"ai-maid:stay\",\"restart\":true}"),
                    call("select-b", "select_command", "{\"restart\":true,\"command_id\":\"ai-maid:stay\"}"), call("state", "query_maid", "{}")));
            model.add(reply("I am staying here. The server accepted the command; this is not a completed work task."));
            send(context, "Please stay here. remember_needle");
            context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat && !chat.busy() && chat.messages().stream().anyMatch(m -> m.getString().contains("I am staying")));
            server.runOnServer(s -> {
                var maid = (AiMaidEntity)connection.getServerLevel().getEntity(id);
                check(maid.getMaidCommand() == MaidCommands.STAY && maid.getMaidCommandRevision() == initialRevision + 1, "Duplicate model call restarted the command");
            });
            check(model.requests.size() == 2, "Tool result did not reach model");
            var toolMessages = model.requests.get(1).getAsJsonArray("messages");
            check(toolMessages.toString().contains("select-a") && toolMessages.toString().contains("select-b") && toolMessages.toString().contains("work_completed"), "Tool call IDs or outcome missing");
            context.takeScreenshot("llm-tool-chat");

            model.add(reply("I remember our conversation. " + "Long replies wrap inside the history panel. ".repeat(18)));
            send(context, "What do you remember?");
            context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat && !chat.busy() && chat.messages().stream().anyMatch(m -> m.getString().contains("I remember")));
            check(model.requests.get(2).toString().contains("remember_needle"), "History not sent");
            context.takeScreenshot("llm-long-reply");

            // 同命令人工重选也必须使旧请求失效。
            var staleGate = model.hold(toolReply(call("stale", "select_command", "{\"command_id\":\"ai-maid:free\"}")));
            send(context, "A delayed request"); context.waitFor(c -> model.requests.size() == 4);
            long manualRevision = server.computeOnServer(s -> {
                var maid = (AiMaidEntity)connection.getServerLevel().getEntity(id); maid.selectMaidCommand(MaidCommands.STAY); return maid.getMaidCommandRevision();
            });
            context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat && !chat.busy()); staleGate.countDown(); context.waitTicks(8);
            server.runOnServer(s -> check(((AiMaidEntity)connection.getServerLevel().getEntity(id)).getMaidCommandRevision() == manualRevision, "Stale response changed work"));
            context.takeScreenshot("llm-stale-cancelled");

            var cancelGate = model.hold(toolReply(call("cancelled", "select_command", "{\"command_id\":\"ai-maid:free\"}")));
            send(context, "Cancel this request"); context.waitFor(c -> model.requests.size() == 5);
            context.clickScreenButton("llm.ai-maid.cancel"); context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat && !chat.busy());
            cancelGate.countDown(); context.waitTicks(8);
            server.runOnServer(s -> check(((AiMaidEntity)connection.getServerLevel().getEntity(id)).getMaidCommand() == MaidCommands.STAY, "Cancelled tool changed command"));

            context.clickScreenButton("llm.ai-maid.clear");
            context.waitFor(c -> ((MaidChatScreen)c.gui.screen()).messages().size() == 1);
            model.add(reply("A new conversation.")); send(context, "Fresh start");
            context.waitFor(c -> !((MaidChatScreen)c.gui.screen()).busy());
            check(!model.requests.get(5).toString().contains("remember_needle"), "Cleared history leaked into request");

            // 第三方输出异常不会崩溃或改变工作。
            model.add("{bad-json"); send(context, "Malformed service response");
            context.waitFor(c -> !((MaidChatScreen)c.gui.screen()).busy()); context.takeScreenshot("llm-response-error");

            // 每只 Maid 使用独立历史；用真实 C2S open/send 进入第二只 Maid。
            UUID second = server.computeOnServer(s -> {
                var owner = connection.getServerPlayer(); var maid = new AiMaidEntity(connection.getServerLevel());
                maid.setPos(owner.position().add(2, 0, 0)); maid.setNoAi(true); maid.setTame(true, true); maid.setOwner(owner);
                connection.getServerLevel().addFreshEntity(maid); return maid.getUUID();
            });
            context.runOnClient(c -> ClientPlayNetworking.send(new ChatRequest(second, "open", "")));
            context.waitFor(c -> c.gui.screen() instanceof MaidChatScreen chat && chat.maid().equals(second));
            model.add(reply("Hello from the second maid.")); send(context, "Hello second maid");
            context.waitFor(c -> !((MaidChatScreen)c.gui.screen()).busy());
            check(!model.requests.get(7).toString().contains("Fresh start"), "History leaked between maids");

            // 角色设定属于实体，清会话不会清掉角色设定。
            context.runOnClient(c -> ClientPlayNetworking.send(new ChatRequest(second, "persona", "A calm gardener")));
            server.waitFor(s -> ((AiMaidEntity)connection.getServerLevel().getEntity(second)).getAttachedOrElse(LlmData.PERSONA, "").equals("A calm gardener"));
            model.add(reply("Persona applied.")); send(context, "Your personality?");
            context.waitFor(c -> !((MaidChatScreen)c.gui.screen()).busy());
            check(model.requests.get(8).toString().contains("A calm gardener"), "Persona not used");

            var reloadGate = model.hold(reply("should never appear")); send(context, "Reload during request");
            context.waitFor(c -> model.requests.size() == 10);
            server.runOnServer(s -> LlmAddon.install(s, null)); reloadGate.countDown();
            context.waitFor(c -> !((MaidChatScreen)c.gui.screen()).busy());
            send(context, "Disabled provider"); context.waitFor(c -> !((MaidChatScreen)c.gui.screen()).busy());
            context.waitTicks(8); check(model.requests.size() == 10, "Disabled provider contacted network");
            context.runOnClient(c -> check(((MaidChatScreen)c.gui.screen()).messages().stream().noneMatch(m -> m.getString().contains("should never appear")), "Old service reply survived reload"));
            context.takeScreenshot("llm-disabled");

            // 查询是实时快照；同一轮只接受一种工作，不能伪装成工作队列。
            server.runOnServer(s -> LlmAddon.install(s, model.config()));
            model.add(toolReply(call("before", "query_maid", "{}"),
                    call("one-work", "select_command", "{\"command_id\":\"ai-maid:free\"}"),
                    call("after", "query_maid", "{}"),
                    call("second-work", "select_command", "{\"command_id\":\"ai-maid:stay\"}")));
            model.add(reply("Only one work strategy was accepted."));
            send(context, "Try one work strategy"); context.waitFor(c -> !((MaidChatScreen)c.gui.screen()).busy());
            var batch = model.requests.get(11).getAsJsonArray("messages");
            var outputs = new HashMap<String, JsonObject>();
            for (var element : batch) {
                var message = element.getAsJsonObject();
                if (message.has("tool_call_id")) outputs.put(message.get("tool_call_id").getAsString(), ChatProtocol.object(message.get("content").getAsString()));
            }
            check(outputs.get("before").get("command").getAsString().equals("ai-maid:follow"), "Initial snapshot incorrect");
            check(outputs.get("after").get("command").getAsString().equals("ai-maid:free"), "Repeated query returned stale data");
            check(outputs.get("second-work").get("error").getAsString().equals("only_one_selection_per_turn"), "Multiple works accepted");

            // 无穷工具调用会到达上限，并释放会话占用。
            for (int i = 0; i < 3; i++) model.add(toolReply(call("loop-" + i, "query_maid", "{}")));
            send(context, "Looping provider"); context.waitFor(c -> !((MaidChatScreen)c.gui.screen()).busy());
            check(model.requests.size() == 15, "Tool loop not bounded");

            var rangeGate = model.hold(toolReply(call("far", "select_command", "{\"command_id\":\"ai-maid:stay\"}")));
            send(context, "Moving out of range"); context.waitFor(c -> model.requests.size() == 16);
            server.runOnServer(s -> ((AiMaidEntity)connection.getServerLevel().getEntity(second)).setPos(connection.getServerPlayer().position().add(40, 0, 0)));
            context.waitFor(c -> !((MaidChatScreen)c.gui.screen()).busy()); rangeGate.countDown(); context.waitTicks(5);
            server.runOnServer(s -> {
                var maid = (AiMaidEntity)connection.getServerLevel().getEntity(second);
                check(maid.getMaidCommand() == MaidCommands.FREE, "Out-of-range response selected work"); maid.setPos(connection.getServerPlayer().position().add(2, 0, 0));
            });

            var ownerGate = model.hold(reply("old owner's response")); send(context, "Changing owner");
            context.waitFor(c -> model.requests.size() == 17);
            server.runOnServer(s -> ((AiMaidEntity)connection.getServerLevel().getEntity(second)).setTame(false, false));
            context.waitFor(c -> !((MaidChatScreen)c.gui.screen()).busy()); ownerGate.countDown(); context.waitTicks(5);
            server.runOnServer(s -> {
                var maid = (AiMaidEntity)connection.getServerLevel().getEntity(second); maid.setTame(true, true); maid.setOwner(connection.getServerPlayer());
                LlmAddon.install(s, model.limitedConfig());
            });
            var capacityGate = model.hold(reply("held")); send(context, "Occupy one global slot");
            context.waitFor(c -> model.requests.size() == 18);
            server.runOnServer(s -> check(!LlmAddon.service(s).chat(connection.getServerPlayer(), (AiMaidEntity)connection.getServerLevel().getEntity(id), "over capacity"), "Global capacity not enforced"));
            context.clickScreenButton("llm.ai-maid.cancel"); context.waitFor(c -> !((MaidChatScreen)c.gui.screen()).busy()); capacityGate.countDown();
            server.runOnServer(s -> check(!LlmAddon.service(s).chat(connection.getServerPlayer(), (AiMaidEntity)connection.getServerLevel().getEntity(id), "over cooldown"), "Player cooldown did not apply across maids"));
            check(model.requests.size() == 18, "Rejected requests contacted provider");
            server.runOnServer(s -> LlmAddon.install(s, model.config()));
            model.hold(reply("should be cancelled on shutdown")); send(context, "Shutdown during request");
            context.waitFor(c -> model.requests.size() == 19);
        } catch (Exception e) { throw new AssertionError("LLM client acceptance failed", e); }
    }
    private static void send(ClientGameTestContext context, String message) {
        context.runOnClient(c -> ((MaidChatScreen)c.gui.screen()).setInput(message));
        context.clickScreenButton("llm.ai-maid.send");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static JsonObject call(String id, String name, String args) {
        var call = new JsonObject(); call.addProperty("id", id); call.addProperty("type", "function");
        var fn = new JsonObject(); fn.addProperty("name", name); fn.addProperty("arguments", args); call.add("function", fn); return call;
    }
    private static String toolReply(JsonObject... calls) {
        var message = ChatProtocol.message("assistant", ""); var array = new JsonArray(); for (var call : calls) array.add(call); message.add("tool_calls", array);
        return response(message, "tool_calls");
    }
    private static String reply(String text) { return response(ChatProtocol.message("assistant", text), "stop"); }
    private static String response(JsonObject message, String reason) {
        var choice = new JsonObject(); choice.addProperty("finish_reason", reason); choice.add("message", message);
        var choices = new JsonArray(); choices.add(choice); var root = new JsonObject(); root.add("choices", choices); return root.toString();
    }
    private static final class MockModel implements AutoCloseable {
        private record Planned(String text, CountDownLatch gate) {}
        final HttpServer http;
        final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        final BlockingQueue<Planned> replies = new LinkedBlockingQueue<>();
        final List<JsonObject> requests = new CopyOnWriteArrayList<>();
        MockModel() throws Exception {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); http.setExecutor(executor);
            http.createContext("/chat", exchange -> {
                try {
                    requests.add(ChatProtocol.object(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                    var planned = replies.poll(5, TimeUnit.SECONDS); if (planned == null) throw new IllegalStateException("Unexpected model request");
                    planned.gate().await(15, TimeUnit.SECONDS);
                    var bytes = planned.text().getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
            }); http.start();
        }
        LlmConfig config() { return new LlmConfig("http://127.0.0.1:" + http.getAddress().getPort() + "/chat", "mock", "", "test", "max_tokens", 10, 512, 2, 0, 2, 2, 32); }
        LlmConfig limitedConfig() { return new LlmConfig("http://127.0.0.1:" + http.getAddress().getPort() + "/chat", "mock", "", "test", "max_tokens", 10, 512, 2, 60, 1, 2, 32); }
        void add(String reply) { replies.add(new Planned(reply, new CountDownLatch(0))); }
        CountDownLatch hold(String reply) { var gate = new CountDownLatch(1); replies.add(new Planned(reply, gate)); return gate; }
        public void close() { http.stop(0); executor.shutdownNow(); }
    }
}

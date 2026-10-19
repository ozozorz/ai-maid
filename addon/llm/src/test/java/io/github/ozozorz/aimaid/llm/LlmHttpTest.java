package io.github.ozozorz.aimaid.llm;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import io.github.ozozorz.aimaid.llm.config.LlmConfig;
import io.github.ozozorz.aimaid.llm.http.LlmClient;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LlmHttpTest {
    HttpServer server;
    ExecutorService executor;
    volatile int status = 200, delay;
    volatile String response = LlmProtocolTest.response("收到", "stop"), authorization, request;
    @BeforeEach void start() throws Exception {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.setExecutor(executor);
        server.createContext("/chat", exchange -> {
            request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            try { var bytes = response.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); }
            finally { exchange.close(); }
        }); server.start();
    }
    @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }
    LlmClient client(int timeout, String key) {
        return new LlmClient(new LlmConfig("http://127.0.0.1:" + server.getAddress().getPort() + "/chat", "mock-model", key, "test", "max_completion_tokens", timeout, 128, 2, 0, 2, 2, 32));
    }
    CompletableFuture<ChatProtocol.Reply> ask(LlmClient client) { return client.complete(List.of(ChatProtocol.message("user", "你好")), new JsonArray()); }
    @Test void postsUtf8AndConfiguredTokenFieldAndOptionalAuthentication() throws Exception {
        try (var c = client(3, "test-secret")) { assertEquals("收到", ask(c).get(5, TimeUnit.SECONDS).text()); }
        var json = ChatProtocol.object(request); assertEquals("mock-model", json.get("model").getAsString());
        assertTrue(json.has("max_completion_tokens")); assertFalse(json.get("stream").getAsBoolean());
        assertEquals("Bearer test-secret", authorization); assertTrue(request.contains("你好")); assertFalse(request.contains("test-secret"));
        try (var c = client(3, "")) { ask(c).get(5, TimeUnit.SECONDS); } assertNull(authorization);
    }
    @Test void rejectsServiceErrorsWithoutEchoingBody() {
        status = 401; response = "secret echoed by provider";
        try (var c = client(3, "")) {
            var e = assertThrows(CompletionException.class, () -> ask(c).join()); assertEquals("http_401", LlmClient.errorCode(e));
        }
    }
    @Test void rejectsMalformedAndOversizedBodies() {
        try (var c = client(3, "")) {
            response = "not json"; assertEquals("invalid_response", LlmClient.errorCode(assertThrows(CompletionException.class, () -> ask(c).join())));
            response = "x".repeat(262145); assertEquals("response_too_large", LlmClient.errorCode(assertThrows(CompletionException.class, () -> ask(c).join())));
        }
    }
    @Test void timesOutAndCanCancelWithoutWaitingForServer() {
        delay = 2500;
        try (var c = client(1, "")) {
            assertEquals("timeout", LlmClient.errorCode(assertThrows(CompletionException.class, () -> ask(c).join())));
            var pending = ask(c); assertTrue(pending.cancel(true)); assertTrue(pending.isCancelled());
        }
    }
    @Test void doesNotFollowRedirects() {
        status = 302;
        try (var c = client(3, "")) { assertEquals("http_302", LlmClient.errorCode(assertThrows(CompletionException.class, () -> ask(c).join()))); }
    }
}

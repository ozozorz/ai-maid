package io.github.ozozorz.aimaid.llm.http;

import com.google.gson.*;
import io.github.ozozorz.aimaid.llm.config.LlmConfig;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/** 异步 HTTP；限制响应体，关闭时立即取消请求，不阻塞游戏线程。 */
public final class LlmClient implements AutoCloseable {
    private final LlmConfig config;
    private final HttpClient http;

    public LlmClient(LlmConfig config) {
        this.config = config;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public CompletableFuture<ChatProtocol.Reply> complete(List<JsonObject> messages, JsonArray tools) {
        var body = new JsonObject();
        body.addProperty("model", config.getModel());
        var array = new JsonArray();
        messages.forEach(array::add);
        body.add("messages", array);
        body.addProperty("stream", false);
        body.addProperty(config.tokenField(), config.maxTokens());
        body.add("tools", tools);
        body.addProperty("tool_choice", "auto");
        // DeepSeek 官方接口默认开启思考模式。
        // 当前先使用非思考模式，匹配现有的工具对话协议。
        if ("api.deepseek.com".equalsIgnoreCase(
                URI.create(config.getApiUrl()).getHost())) {
            var thinking = new JsonObject();
            thinking.addProperty("type", "disabled");
            body.add("thinking", thinking);
        }
        String json = body.toString();
        if (json.length() > 131072)
            return CompletableFuture.failedFuture(new Failure("context_too_large"));
        var builder = HttpRequest.newBuilder(URI.create(config.getApiUrl()))
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (!config.getApiKey().isEmpty())
            builder.header("Authorization", "Bearer " + config.getApiKey());
        var request = http.sendAsync(builder.build(), info -> new LimitedBody());
        var result = request.thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new CompletionException(new Failure("http_" + response.statusCode()));
            try {
                return ChatProtocol.parseReply(response.body());
            } catch (IllegalArgumentException e) {
                throw new CompletionException(new Failure("invalid_response"));
            }
        }).orTimeout(config.timeoutSeconds(), TimeUnit.SECONDS);
        result.whenComplete((r, e) -> {
            if (e != null)
                request.cancel(true);
        });
        return result;
    }

    public static String errorCode(Throwable error) {
        while (error instanceof CompletionException || error instanceof ExecutionException)
            error = error.getCause();
        if (error instanceof Failure f)
            return f.getMessage();
        if (error instanceof TimeoutException || error instanceof HttpTimeoutException)
            return "timeout";
        return "connection";
    }

    public static final class Failure extends RuntimeException {
        public Failure(String code) {
            super(code);
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<String> {
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        public CompletionStage<String> getBody() {
            return result;
        }

        public void onSubscribe(Flow.Subscription s) {
            subscription = s;
            s.request(1);
        }

        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 262144 - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new Failure("response_too_large"));
                    return;
                }
                var chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        public void onError(Throwable error) {
            result.completeExceptionally(error);
        }

        public void onComplete() {
            result.complete(bytes.toString(StandardCharsets.UTF_8));
        }
    }

    @Override
    public void close() {
        http.shutdownNow();
    }
}

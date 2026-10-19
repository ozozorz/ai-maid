package io.github.ozozorz.aimaid.llm.config;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;

/** 首次创建关闭的模板；旧的四字段配置仍有效，新增字段取默认值。 */
public final class LlmConfigLoader {
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final String DEFAULT_JSON = """
        {
          "enabled": false,
          "apiUrl": "",
          "model": "",
          "apiKey": "",
          "systemPrompt": "你是一位友善的 Minecraft 女仆。使用主人的语言简洁回答。",
          "tokenField": "max_tokens",
          "timeoutSeconds": 45,
          "maxTokens": 1024,
          "historyTurns": 8,
          "cooldownSeconds": 2,
          "maxConcurrent": 4,
          "maxToolRounds": 4,
          "chatDistance": 32
        }
        """;
    private LlmConfigLoader() {}
    public static Optional<LlmConfig> load(Path path) throws IOException {
        if (Files.notExists(path)) {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, DEFAULT_JSON, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        if (Files.size(path) > 65536) throw new IllegalArgumentException("配置文件超过 64 KiB");
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }
    public static Optional<LlmConfig> parse(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null) throw new IllegalArgumentException("配置必须是 JSON 对象");
        var enabled = root.get("enabled");
        if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("enabled 必须是布尔值");
        if (!enabled.getAsBoolean()) return Optional.empty();
        return Optional.of(new LlmConfig(string(root, "apiUrl", null), string(root, "model", null), string(root, "apiKey", null),
                string(root, "systemPrompt", "你是一位友善的 Minecraft 女仆。使用主人的语言简洁回答。"),
                string(root, "tokenField", "max_tokens"), integer(root, "timeoutSeconds", 45), integer(root, "maxTokens", 1024),
                integer(root, "historyTurns", 8), integer(root, "cooldownSeconds", 2), integer(root, "maxConcurrent", 4),
                integer(root, "maxToolRounds", 4), integer(root, "chatDistance", 32)));
    }
    private static String string(JsonObject root, String field, String fallback) {
        if (!root.has(field) && fallback != null) return fallback;
        var value = root.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(field + " 必须是字符串");
        return value.getAsString();
    }
    private static int integer(JsonObject root, String field, int fallback) {
        if (!root.has(field)) return fallback;
        var value = root.get(field);
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new ArithmeticException();
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException e) { throw new IllegalArgumentException(field + " 必须是整数"); }
    }
}

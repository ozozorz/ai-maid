package io.github.ozozorz.aimaid.llm.protocol;

import com.google.gson.*;
import java.util.*;

/** 只处理 JSON 数据，不持有 Entity、Level 或 MinecraftServer。 */
public final class ChatProtocol {
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    public record ToolCall(String id, String name, String arguments) {}
    public record Reply(String text, List<ToolCall> calls, JsonObject message) {}
    private ChatProtocol() {}
    public static JsonObject object(String json) {
        try {
            var value = GSON.fromJson(json, JsonElement.class);
            if (value == null || !value.isJsonObject()) throw new IllegalArgumentException();
            return value.getAsJsonObject();
        } catch (RuntimeException e) { throw new IllegalArgumentException("invalid_json"); }
    }
    public static String string(JsonObject obj, String key, int max) {
        var value = obj.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().length() > max) throw new IllegalArgumentException("invalid_" + key);
        return value.getAsString();
    }
    public static Reply parseReply(String body) {
        try {
            var choice = object(body).getAsJsonArray("choices").get(0).getAsJsonObject();
            var reason = string(choice, "finish_reason", 40);
            // 截断的参数绝不能触发游戏动作。
            if (!reason.equals("stop") && !reason.equals("tool_calls")) throw new IllegalArgumentException();
            var source = choice.getAsJsonObject("message");
            if (!string(source, "role", 20).equals("assistant")) throw new IllegalArgumentException();
            String text = source.has("content") && !source.get("content").isJsonNull() ? string(source, "content", 8192) : "";
            List<ToolCall> calls = new ArrayList<>(); Set<String> ids = new HashSet<>();
            if (source.has("tool_calls") && !source.get("tool_calls").isJsonNull()) {
                var array = source.getAsJsonArray("tool_calls");
                if (array.size() > 4) throw new IllegalArgumentException();
                for (var element : array) {
                    var call = element.getAsJsonObject(); String id = string(call, "id", 128);
                    if (id.isBlank() || !ids.add(id) || !string(call, "type", 20).equals("function")) throw new IllegalArgumentException();
                    var fn = call.getAsJsonObject("function");
                    calls.add(new ToolCall(id, string(fn, "name", 64), string(fn, "arguments", 4096)));
                }
            }
            if (text.isBlank() && calls.isEmpty()) throw new IllegalArgumentException();
            var clean = message("assistant", text);
            if (!calls.isEmpty()) {
                var array = new JsonArray();
                for (var c : calls) {
                    var fn = new JsonObject(); fn.addProperty("name", c.name()); fn.addProperty("arguments", c.arguments());
                    var call = new JsonObject(); call.addProperty("id", c.id()); call.addProperty("type", "function"); call.add("function", fn); array.add(call);
                }
                clean.add("tool_calls", array);
            }
            return new Reply(text, List.copyOf(calls), clean);
        } catch (RuntimeException e) { throw new IllegalArgumentException("invalid_response"); }
    }
    public static JsonObject message(String role, String text) {
        var obj = new JsonObject(); obj.addProperty("role", role); obj.addProperty("content", text); return obj;
    }
    public static JsonObject toolResult(String id, JsonObject result) {
        var obj = message("tool", result.toString()); obj.addProperty("tool_call_id", id); return obj;
    }
    public static JsonObject error(String code) {
        var result = new JsonObject(); result.addProperty("ok", false); result.addProperty("error", code); return result;
    }
}

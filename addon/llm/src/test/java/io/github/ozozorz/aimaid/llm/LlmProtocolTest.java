package io.github.ozozorz.aimaid.llm;

import com.google.gson.*;
import io.github.ozozorz.aimaid.llm.config.*;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class LlmProtocolTest {
    @TempDir Path directory;
    @Test void createsDisabledTemplateWithoutOverwriting() throws Exception {
        var file = directory.resolve("llm.json"); assertTrue(LlmConfigLoader.load(file).isEmpty());
        String custom = "{\"enabled\":false,\"notes\":\"keep me\"}"; Files.writeString(file, custom);
        LlmConfigLoader.load(file); assertEquals(custom, Files.readString(file));
    }
    @Test void acceptsLegacyConfigAndLocalNoAuth() {
        var config = LlmConfigLoader.parse("{\"enabled\":true,\"apiUrl\":\"http://127.0.0.1:8080/v1/chat/completions\",\"model\":\"local\",\"apiKey\":\"\"}").orElseThrow();
        assertEquals(45, config.timeoutSeconds()); assertEquals(8, config.historyTurns());
    }
    @Test void rejectsInvalidTypesAndRangesWithoutLeakingSecrets() {
        var root = ChatProtocol.object("{\"enabled\":true,\"apiUrl\":\"https://example.test/v1/chat/completions\",\"model\":\"mock\",\"apiKey\":\"secret-value\"}");
        for (String invalid : new String[]{"null", "\"2\"", "2.5", "-1", "99999"}) {
            root.add("timeoutSeconds", JsonParser.parseString(invalid));
            var e = assertThrows(IllegalArgumentException.class, () -> LlmConfigLoader.parse(root.toString()));
            assertFalse(e.toString().contains("secret-value"));
        }
        assertThrows(IllegalArgumentException.class, () -> new LlmConfig("https://user:secret@example.test/", "x", "key"));
        assertThrows(IllegalArgumentException.class, () -> new LlmConfig("file:///secret", "x", "key"));
        assertThrows(IllegalArgumentException.class, () -> new LlmConfig("https://example.test/", "x", "x\nInjected: header"));
        assertFalse(new LlmConfig("https://example.test/", "x", "secret-value").toString().contains("secret"));
    }
    @Test void strictJsonAndFinishReasonRejectPartialActions() {
        assertThrows(IllegalArgumentException.class, () -> ChatProtocol.object("{x:1}"));
        assertThrows(IllegalArgumentException.class, () -> ChatProtocol.object("{} trailing"));
        assertThrows(IllegalArgumentException.class, () -> ChatProtocol.object("[]"));
        for (String reason : new String[]{"length", "content_filter", "function_call"})
            assertThrows(IllegalArgumentException.class, () -> ChatProtocol.parseReply(response("hello", reason)));
    }
    @Test void preservesToolIdsAndValidatesWholeBatchBeforeExecution() {
        String call = "{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"query_maid\",\"arguments\":\"{}\"}}";
        String body = "{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[" + call + "]}}]}";
        var reply = ChatProtocol.parseReply(body); assertEquals("call_1", reply.calls().getFirst().id());
        assertEquals("call_1", ChatProtocol.toolResult("call_1", new JsonObject()).get("tool_call_id").getAsString());
        assertThrows(IllegalArgumentException.class, () -> ChatProtocol.parseReply(body.replace(call, call + "," + call)));
        assertThrows(IllegalArgumentException.class, () -> ChatProtocol.parseReply(response("x".repeat(8193), "stop")));
    }
    static String response(String text, String reason) {
        var choice = new JsonObject(); choice.addProperty("finish_reason", reason); choice.add("message", ChatProtocol.message("assistant", text));
        var choices = new JsonArray(); choices.add(choice); var root = new JsonObject(); root.add("choices", choices); return root.toString();
    }
}

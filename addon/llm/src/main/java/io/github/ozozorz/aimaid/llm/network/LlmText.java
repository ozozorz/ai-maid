package io.github.ozozorz.aimaid.llm.network;

import net.minecraft.network.chat.Component;

public final class LlmText {
    private LlmText() {}
    public static Component line(String kind, String text) {
        if (kind.equals("task")) {
            try {
                return net.minecraft.network.chat.ComponentSerialization.CODEC
                        .parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(text))
                        .result().orElse(Component.translatable("llm.ai-maid.message.invalid_response"));
            } catch (RuntimeException e) { return Component.translatable("llm.ai-maid.message.invalid_response"); }
        }
        if (kind.equals("error") || kind.equals("info") || kind.equals("clear")) {
            String code = text.startsWith("http_") && !text.equals("http_401") && !text.equals("http_403") && !text.equals("http_429") ? "http" : text;
            return Component.translatable("llm.ai-maid.message." + code);
        }
        return Component.translatable("llm.ai-maid.line." + kind, Component.literal(text.replace('§', ' ')));
    }
}

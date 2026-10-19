package io.github.ozozorz.aimaid.llm.network;

import net.minecraft.network.chat.Component;

public final class LlmText {
    private LlmText() {}
    public static Component line(String kind, String text) {
        if (kind.equals("error") || kind.equals("info") || kind.equals("clear")) {
            String code = text.startsWith("http_") && !text.equals("http_401") && !text.equals("http_403") && !text.equals("http_429") ? "http" : text;
            return Component.translatable("llm.ai-maid.message." + code);
        }
        return Component.translatable("llm.ai-maid.line." + kind, Component.literal(text.replace('§', ' ')));
    }
}

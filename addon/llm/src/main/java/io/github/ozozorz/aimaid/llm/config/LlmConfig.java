package io.github.ozozorz.aimaid.llm.config;

import java.net.URI;
import java.util.Objects;

/** 不可变的服务端配置；不会作为网络包发给客户端，也不输出密钥。 */
public final class LlmConfig {
    private final String apiUrl, model, apiKey, systemPrompt, tokenField;
    private final int timeoutSeconds, maxTokens, historyTurns, cooldownSeconds, maxConcurrent, maxToolRounds, chatDistance;

    public LlmConfig(String apiUrl, String model, String apiKey) {
        this(apiUrl, model, apiKey, "你是一位友善的 Minecraft 女仆。使用主人的语言简洁回答。", "max_tokens", 45, 1024, 8, 2, 4, 4, 32);
    }
    public LlmConfig(String apiUrl, String model, String apiKey, String systemPrompt, String tokenField,
                     int timeoutSeconds, int maxTokens, int historyTurns, int cooldownSeconds,
                     int maxConcurrent, int maxToolRounds, int chatDistance) {
        this.apiUrl = Objects.requireNonNull(apiUrl).strip();
        this.model = Objects.requireNonNull(model).strip();
        this.apiKey = Objects.requireNonNull(apiKey);
        this.systemPrompt = Objects.requireNonNull(systemPrompt);
        this.tokenField = Objects.requireNonNull(tokenField);
        URI uri;
        try { uri = URI.create(this.apiUrl); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("apiUrl 地址格式不正确"); }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("apiUrl 必须为完整 HTTP(S) 地址，不含用户信息或片段");
        if (this.model.isEmpty() || this.model.length() > 200) throw new IllegalArgumentException("model 长度应为 1..200");
        if (apiKey.length() > 4096 || apiKey.chars().anyMatch(c -> c < 32 || c > 126))
            throw new IllegalArgumentException("apiKey 必须为可用的 HTTP 认证字符串");
        if (systemPrompt.length() > 4000) throw new IllegalArgumentException("systemPrompt 不能超过 4000 字符");
        if (!tokenField.equals("max_tokens") && !tokenField.equals("max_completion_tokens"))
            throw new IllegalArgumentException("tokenField 应为 max_tokens 或 max_completion_tokens");
        this.timeoutSeconds = range("timeoutSeconds", timeoutSeconds, 1, 120);
        this.maxTokens = range("maxTokens", maxTokens, 64, 8192);
        this.historyTurns = range("historyTurns", historyTurns, 0, 20);
        this.cooldownSeconds = range("cooldownSeconds", cooldownSeconds, 0, 60);
        this.maxConcurrent = range("maxConcurrent", maxConcurrent, 1, 16);
        this.maxToolRounds = range("maxToolRounds", maxToolRounds, 1, 8);
        this.chatDistance = range("chatDistance", chatDistance, 4, 64);
    }
    private static int range(String field, int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException(field + " 超出范围 " + min + ".." + max);
        return value;
    }
    public String getApiUrl() { return apiUrl; }
    public String getModel() { return model; }
    public String getApiKey() { return apiKey; }
    public String systemPrompt() { return systemPrompt; }
    public String tokenField() { return tokenField; }
    public int timeoutSeconds() { return timeoutSeconds; }
    public int maxTokens() { return maxTokens; }
    public int historyTurns() { return historyTurns; }
    public int cooldownSeconds() { return cooldownSeconds; }
    public int maxConcurrent() { return maxConcurrent; }
    public int maxToolRounds() { return maxToolRounds; }
    public int chatDistance() { return chatDistance; }
    @Override public String toString() { return "LlmConfig[redacted]"; }
}

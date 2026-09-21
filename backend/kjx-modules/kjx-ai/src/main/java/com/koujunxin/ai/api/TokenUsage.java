package com.koujunxin.ai.api;

public record TokenUsage(int promptTokens, int completionTokens, int cachedTokens) {

    public TokenUsage {
        if (promptTokens < 0 || completionTokens < 0 || cachedTokens < 0) {
            throw new IllegalArgumentException("token counts must be >= 0");
        }
    }

    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0);
    }

    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}

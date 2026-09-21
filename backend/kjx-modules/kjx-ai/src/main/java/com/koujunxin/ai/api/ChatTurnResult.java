package com.koujunxin.ai.api;

import com.koujunxin.ai.openai.OpenAiCompletionResponse;

public record ChatTurnResult(
        String turnId,
        String promptVersion,
        RoutedModel route,
        OpenAiCompletionResponse response,
        TokenUsage usage,
        boolean degradedToText
) {

    public ChatTurnResult {
        if (turnId == null || turnId.isBlank()) {
            throw new IllegalArgumentException("turnId is required");
        }
        if (route == null) {
            throw new IllegalArgumentException("route is required");
        }
        if (response == null) {
            throw new IllegalArgumentException("response is required");
        }
        if (usage == null) {
            usage = TokenUsage.zero();
        }
    }
}

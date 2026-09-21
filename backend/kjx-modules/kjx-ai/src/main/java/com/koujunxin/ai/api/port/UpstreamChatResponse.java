package com.koujunxin.ai.api.port;

import java.util.List;

import com.koujunxin.ai.api.TokenUsage;
import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.openai.OpenAiToolCall;

public record UpstreamChatResponse(
        String id,
        String model,
        OpenAiMessage message,
        String finishReason,
        TokenUsage usage
) {

    public UpstreamChatResponse {
        if (message == null) {
            throw new IllegalArgumentException("message is required");
        }
        if (finishReason == null || finishReason.isBlank()) {
            finishReason = "stop";
        }
        if (usage == null) {
            usage = TokenUsage.zero();
        }
        if (id == null || id.isBlank()) {
            id = "chatcmpl-local";
        }
        if (model == null) {
            model = "";
        }
    }

    public List<OpenAiToolCall> toolCalls() {
        return message.toolCalls();
    }

    public String content() {
        return message.content() == null ? "" : message.content();
    }
}

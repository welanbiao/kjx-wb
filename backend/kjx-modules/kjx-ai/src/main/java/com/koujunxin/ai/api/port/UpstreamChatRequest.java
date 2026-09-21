package com.koujunxin.ai.api.port;

import java.util.List;

import com.koujunxin.ai.api.ByokSecret;
import com.koujunxin.ai.api.RoutedModel;
import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.openai.OpenAiTool;

public record UpstreamChatRequest(
        RoutedModel route,
        List<OpenAiMessage> messages,
        List<OpenAiTool> tools,
        String jsonSchema,
        Integer maxTokens,
        boolean stream,
        ByokSecret secret
) {

    public UpstreamChatRequest {
        if (route == null) {
            throw new IllegalArgumentException("route is required");
        }
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        if (route.channel() == com.koujunxin.ai.api.AiChannelMode.PLATFORM) {
            secret = null;
        }
    }

    @Override
    public String toString() {
        return "UpstreamChatRequest[model=" + route.modelId()
                + ", channel=" + route.channel()
                + ", messages=" + messages.size()
                + ", tools=" + tools.size()
                + ", stream=" + stream
                + "]";
    }
}

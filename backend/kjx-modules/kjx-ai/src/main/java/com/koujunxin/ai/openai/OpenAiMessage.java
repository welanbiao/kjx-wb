package com.koujunxin.ai.openai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiMessage(
        String role,
        String content,
        @JsonProperty("tool_call_id") String toolCallId,
        @JsonProperty("tool_calls") List<OpenAiToolCall> toolCalls,
        String name
) {

    public OpenAiMessage {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        role = role.trim();
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static OpenAiMessage system(String content) {
        return new OpenAiMessage("system", content, null, List.of(), null);
    }

    public static OpenAiMessage user(String content) {
        return new OpenAiMessage("user", content, null, List.of(), null);
    }

    public static OpenAiMessage assistant(String content) {
        return new OpenAiMessage("assistant", content, null, List.of(), null);
    }

    public static OpenAiMessage assistantTools(List<OpenAiToolCall> calls) {
        return new OpenAiMessage("assistant", null, null, calls, null);
    }

    public static OpenAiMessage tool(String toolCallId, String content) {
        return new OpenAiMessage("tool", content, toolCallId, List.of(), null);
    }

    public boolean isSystem() {
        return "system".equalsIgnoreCase(role);
    }
}

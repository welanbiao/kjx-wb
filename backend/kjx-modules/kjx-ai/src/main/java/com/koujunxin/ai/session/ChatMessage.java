package com.koujunxin.ai.session;

import java.time.Instant;
import java.util.List;

import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.openai.OpenAiToolCall;

public record ChatMessage(
        String sessionId,
        int seq,
        String turnId,
        String role,
        String content,
        String toolCallId,
        List<OpenAiToolCall> toolCalls,
        int tokenCount,
        Instant createdAt
) {

    public ChatMessage {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (turnId == null || turnId.isBlank()) {
            throw new IllegalArgumentException("turnId is required");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        role = role.trim();
        if ("system".equalsIgnoreCase(role)) {
            throw new IllegalArgumentException("system messages are server-assembled and must not be stored from clients");
        }
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (tokenCount < 0) {
            tokenCount = 0;
        }
    }

    public boolean isSystem() {
        return false;
    }

    public OpenAiMessage toOpenAi() {
        return new OpenAiMessage(role, content, toolCallId, toolCalls, null);
    }

    public static ChatMessage fromUser(String sessionId, String turnId, String content) {
        return new ChatMessage(sessionId, 0, turnId, "user", content, null, List.of(), estimate(content), Instant.now());
    }

    public static ChatMessage fromAssistant(String sessionId, String turnId, String content, List<OpenAiToolCall> calls) {
        return new ChatMessage(sessionId, 0, turnId, "assistant", content, null, calls, estimate(content), Instant.now());
    }

    public static ChatMessage fromTool(String sessionId, String turnId, String toolCallId, String content) {
        return new ChatMessage(sessionId, 0, turnId, "tool", content, toolCallId, List.of(), estimate(content), Instant.now());
    }

    public ChatMessage withSeq(int nextSeq) {
        return new ChatMessage(sessionId, nextSeq, turnId, role, content, toolCallId, toolCalls, tokenCount, createdAt);
    }

    private static int estimate(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, content.length() / 4);
    }
}

package com.koujunxin.ai.api;

import java.util.List;

import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.openai.OpenAiTool;

public record ChatTurnCommand(
        long accountId,
        String sessionId,
        String workCode,
        String idempotencyKey,
        AiChannelMode channel,
        ByokSecret byokSecret,
        String byokBaseUrl,
        String byokModel,
        AiTaskKind taskKind,
        List<OpenAiMessage> clientMessages,
        List<OpenAiTool> tools,
        String jsonSchema,
        boolean stream,
        Integer maxTokens,
        String traceId,
        String requestId
) {

    public ChatTurnCommand {
        if (accountId <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        sessionId = requireText(sessionId, "sessionId");
        workCode = requireText(workCode, "workCode");
        if (channel == null) {
            channel = AiChannelMode.PLATFORM;
        }
        if (taskKind == null) {
            taskKind = AiTaskKind.CHAT;
        }
        clientMessages = clientMessages == null ? List.of() : List.copyOf(clientMessages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        if (traceId == null || traceId.isBlank()) {
            traceId = "";
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = "";
        }
        if (channel == AiChannelMode.PLATFORM) {
            byokSecret = null;
            byokBaseUrl = null;
            byokModel = null;
        }
    }

    public void wipeSecret() {
        if (byokSecret != null) {
            byokSecret.wipe();
        }
    }

    @Override
    public String toString() {
        return "ChatTurnCommand[accountId=" + accountId
                + ", sessionId=" + sessionId
                + ", workCode=" + workCode
                + ", channel=" + channel
                + ", taskKind=" + taskKind
                + ", stream=" + stream
                + "]";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}

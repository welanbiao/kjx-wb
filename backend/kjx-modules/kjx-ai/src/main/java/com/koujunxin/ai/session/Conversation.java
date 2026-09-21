package com.koujunxin.ai.session;

public record Conversation(
        String sessionId,
        long accountId,
        String workCode,
        String promptVersion,
        String status,
        int messageCount,
        String lastTurnId
) {

    public Conversation {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (accountId <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        if (workCode == null || workCode.isBlank()) {
            throw new IllegalArgumentException("workCode is required");
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
    }
}

package com.koujunxin.ai.session;

public record ConversationSummary(
        String sessionId,
        int untilSeq,
        String summaryText,
        String model
) {

    public ConversationSummary {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (untilSeq < 0) {
            throw new IllegalArgumentException("untilSeq must be >= 0");
        }
        if (summaryText == null) {
            summaryText = "";
        }
    }
}

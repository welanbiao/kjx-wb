package com.koujunxin.runtime.api.dto;

/**
 * 会话状态快照。payloadJson 为中立 JSON 文本，避免泄漏实现模型。
 */
public record SessionStateSnapshot(
        SessionRef session,
        String stage,
        String schemaVersion,
        String payloadJson
) {

    public SessionStateSnapshot {
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        stage = Dtos.requireText(stage, "stage");
        schemaVersion = Dtos.requireText(schemaVersion, "schemaVersion");
        if (payloadJson == null || payloadJson.isBlank()) {
            payloadJson = "{}";
        }
    }
}

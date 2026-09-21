package com.koujunxin.runtime.api.dto;

/**
 * 会话状态变更命令。
 */
public record StateMutation(
        String type,
        String schemaVersion,
        String payloadJson
) {

    public StateMutation {
        type = Dtos.requireText(type, "type");
        schemaVersion = Dtos.requireText(schemaVersion, "schemaVersion");
        if (payloadJson == null || payloadJson.isBlank()) {
            payloadJson = "{}";
        }
    }
}

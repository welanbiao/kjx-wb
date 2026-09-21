package com.koujunxin.runtime.api.dto;

/**
 * 工具执行命令。argumentsJson 为 JSON Schema 校验后的参数快照。
 */
public record ToolExecutionCommand(
        String toolCode,
        String toolVersion,
        SessionRef session,
        long accountId,
        String workCode,
        String idempotencyKey,
        String argumentsJson
) {

    public ToolExecutionCommand {
        toolCode = Dtos.requireText(toolCode, "toolCode");
        toolVersion = Dtos.requireText(toolVersion, "toolVersion");
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        accountId = Dtos.requirePositive(accountId, "accountId");
        workCode = Dtos.requireText(workCode, "workCode");
        idempotencyKey = Dtos.requireText(idempotencyKey, "idempotencyKey");
        if (argumentsJson == null || argumentsJson.isBlank()) {
            argumentsJson = "{}";
        }
    }
}

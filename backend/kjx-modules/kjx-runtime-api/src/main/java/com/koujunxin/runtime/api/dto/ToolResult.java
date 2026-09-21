package com.koujunxin.runtime.api.dto;

/**
 * 工具执行结果。payloadJson 仅承载对调用方安全的中立数据。
 */
public record ToolResult(
        boolean success,
        String code,
        String message,
        String payloadJson
) {

    public ToolResult {
        code = Dtos.requireText(code, "code");
        if (message == null) {
            message = "";
        }
        if (payloadJson == null || payloadJson.isBlank()) {
            payloadJson = "{}";
        }
    }

    public static ToolResult ok(String payloadJson) {
        return new ToolResult(true, "OK", "", payloadJson);
    }

    public static ToolResult fail(String code, String message) {
        return new ToolResult(false, code, message, "{}");
    }
}

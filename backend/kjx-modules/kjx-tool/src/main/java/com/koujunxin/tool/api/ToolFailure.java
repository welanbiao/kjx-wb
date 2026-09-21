package com.koujunxin.tool.api;

import com.koujunxin.runtime.api.dto.ToolResult;

/**
 * 工具执行失败。不表达会员 VALID/EXPIRED；权益与会话归属走 runtime-api 异常。
 */
public final class ToolFailure extends RuntimeException {

    private final String code;
    private final boolean retryable;

    private ToolFailure(String code, String message, boolean retryable) {
        super(message == null ? "" : message);
        this.code = code == null || code.isBlank() ? "TOOL_FAILED" : code;
        this.retryable = retryable;
    }

    public static ToolFailure of(String code, String message) {
        return new ToolFailure(code, message, false);
    }

    public static ToolFailure retryable(String code, String message) {
        return new ToolFailure(code, message, true);
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    public ToolResult toResult() {
        return ToolResult.fail(code, getMessage());
    }
}

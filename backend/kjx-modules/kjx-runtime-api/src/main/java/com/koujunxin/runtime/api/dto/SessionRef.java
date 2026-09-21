package com.koujunxin.runtime.api.dto;

/**
 * 会话引用。字段保持中立，不得引用 runtime/tool Entity。
 */
public record SessionRef(String sessionId, String workCode) {

    public SessionRef {
        sessionId = Dtos.requireText(sessionId, "sessionId");
        workCode = Dtos.requireText(workCode, "workCode");
    }
}

package com.koujunxin.work.web;

import java.util.UUID;

public record ApiEnvelope<T>(int code, String msg, String requestId, T data) {

    public static <T> ApiEnvelope<T> ok(String requestId, T data) {
        return new ApiEnvelope<>(0, "", requestId == null || requestId.isBlank() ? newId() : requestId, data);
    }

    public static <T> ApiEnvelope<T> error(int code, String msg, String requestId) {
        return new ApiEnvelope<>(code, msg, requestId == null || requestId.isBlank() ? newId() : requestId, null);
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

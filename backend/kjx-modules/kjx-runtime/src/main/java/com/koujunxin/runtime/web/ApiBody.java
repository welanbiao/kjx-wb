package com.koujunxin.runtime.web;

public record ApiBody<T>(int code, T data, String msg, String requestId) {

    public static <T> ApiBody<T> ok(T data, String requestId) {
        return new ApiBody<>(0, data, "", requestId);
    }

    public static <T> ApiBody<T> fail(int code, String msg, T data, String requestId) {
        return new ApiBody<>(code, data, msg == null ? "" : msg, requestId);
    }
}

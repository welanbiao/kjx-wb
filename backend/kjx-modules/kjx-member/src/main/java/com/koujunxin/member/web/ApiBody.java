package com.koujunxin.member.web;

public record ApiBody<T>(int code, T data, String msg, String requestId) {

    public static <T> ApiBody<T> ok(T data, String requestId) {
        return of(0, data, "", requestId);
    }

    public static <T> ApiBody<T> of(int code, T data, String msg, String requestId) {
        return new ApiBody<>(
                code,
                data,
                msg == null ? "" : msg,
                requestId == null || requestId.isBlank() ? MemberWebSupport.newId() : requestId);
    }

    public static ApiBody<Void> fail(int code, String msg, String requestId) {
        return new ApiBody<>(code, null, msg, requestId == null || requestId.isBlank() ? MemberWebSupport.newId() : requestId);
    }
}

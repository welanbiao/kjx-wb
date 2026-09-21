package com.koujunxin.member.web;

public record ApiBody<T>(int code, T data, String msg, String requestId) {

    public static <T> ApiBody<T> ok(T data, String requestId) {
        return new ApiBody<>(0, data, "", requestId == null || requestId.isBlank() ? MemberWebSupport.newId() : requestId);
    }

    public static ApiBody<Void> fail(int code, String msg, String requestId) {
        return new ApiBody<>(code, null, msg, requestId == null || requestId.isBlank() ? MemberWebSupport.newId() : requestId);
    }
}

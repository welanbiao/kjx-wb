package com.koujunxin.member.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

public final class MemberWebSupport {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String IDEMPOTENCY = "X-Idempotency-Key";
    public static final String DEVICE_ID = "X-Device-Id";

    private MemberWebSupport() {
    }

    public static String requestId(HttpServletRequest request) {
        String header = request.getHeader(REQUEST_ID);
        if (header == null || header.isBlank()) {
            return newId();
        }
        return header.length() > 64 ? header.substring(0, 64) : header;
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}

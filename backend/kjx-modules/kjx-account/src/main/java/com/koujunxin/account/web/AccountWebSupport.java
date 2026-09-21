package com.koujunxin.account.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

public final class AccountWebSupport {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String DEVICE_ID = "X-Device-Id";
    public static final String IDEMPOTENCY = "X-Idempotency-Key";
    public static final String CLIENT_VERSION = "X-Client-Version";

    private AccountWebSupport() {
    }

    public static String requestId(HttpServletRequest request) {
        String header = request.getHeader(REQUEST_ID);
        if (header == null || header.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return header.length() > 64 ? header.substring(0, 64) : header;
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    public static String platform(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) {
            return "web";
        }
        String lower = ua.toLowerCase();
        if (lower.contains("android")) {
            return "android";
        }
        if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("ios")) {
            return "ios";
        }
        return "web";
    }
}

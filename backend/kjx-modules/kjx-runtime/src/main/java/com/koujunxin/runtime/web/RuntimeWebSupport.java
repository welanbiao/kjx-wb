package com.koujunxin.runtime.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

public final class RuntimeWebSupport {

    public static final String REQUEST_ID = "X-Request-Id";

    private RuntimeWebSupport() {
    }

    public static String requestId(HttpServletRequest request) {
        String header = request.getHeader(REQUEST_ID);
        if (header == null || header.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return header.length() > 64 ? header.substring(0, 64) : header;
    }
}

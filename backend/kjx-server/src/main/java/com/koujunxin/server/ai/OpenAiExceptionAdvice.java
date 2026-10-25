package com.koujunxin.server.ai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.koujunxin.ai.api.AiException;

import cn.dev33.satoken.exception.NotLoginException;

@RestControllerAdvice(basePackages = "com.koujunxin.server.ai")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OpenAiExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(OpenAiExceptionAdvice.class);

    @ExceptionHandler(AiException.class)
    public ResponseEntity<Map<String, Object>> handleAi(AiException ex) {
        log.warn("ai.http.failed name={} http={}", ex.name(), httpStatus(ex));
        return error(httpStatus(ex), ex.name(), ex.code(), safeMessage(ex.getMessage()));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Map<String, Object>> handleNotLogin(NotLoginException ex) {
        return error(401, "AUTH_REQUIRED", 401, "认证失败，无法访问系统资源");
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        return error(400, "INVALID_REQUEST", 400, safeMessage(ex.getMessage()));
    }

    static int httpStatus(AiException ex) {
        if (ex.code() == 1202) {
            return 502;
        }
        return switch (ex.name()) {
            case "CREDENTIAL_INVALID" -> 400;
            case "AI_CANCELLED" -> 499;
            case "AI_UPSTREAM_TIMEOUT" -> 504;
            case "PROMPT_NOT_FOUND" -> 404;
            default -> {
                int code = ex.code();
                yield code >= 100 && code <= 599 ? code : 400;
            }
        };
    }

    static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "请求失败";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("sk-")
                || lower.contains("akia")
                || lower.contains("bearer")
                || lower.contains("api_key")
                || lower.contains("secret_key")
                || lower.contains("stableprefix")
                || lower.contains("promptversion")) {
            return "请求失败";
        }
        return message;
    }

    private static ResponseEntity<Map<String, Object>> error(int http, String name, int code, String message) {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("name", name);
        inner.put("code", code);
        inner.put("message", message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", inner);
        return ResponseEntity.status(http)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}

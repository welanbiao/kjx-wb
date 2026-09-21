package com.koujunxin.runtime.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.error.RuntimePortException;
import com.koujunxin.runtime.api.error.SessionNotOwnedException;

@RestControllerAdvice(basePackages = "com.koujunxin.runtime.web")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RuntimeExceptionAdvice {

    @ExceptionHandler(RuntimeFailure.class)
    public ResponseEntity<ApiBody<Map<String, String>>> handle(RuntimeFailure ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.httpStatus())
                .body(ApiBody.fail(ex.httpStatus(), ex.getMessage(), Map.of("errorName", ex.errorName()),
                        RuntimeWebSupport.requestId(request)));
    }

    @ExceptionHandler(EntitlementRequiredException.class)
    public ResponseEntity<ApiBody<Map<String, String>>> handleEntitlement(
            EntitlementRequiredException ex, HttpServletRequest request) {
        return ResponseEntity.status(403)
                .body(ApiBody.fail(403, ex.getMessage(), Map.of("errorName", ex.code()),
                        RuntimeWebSupport.requestId(request)));
    }

    @ExceptionHandler(SessionNotOwnedException.class)
    public ResponseEntity<ApiBody<Map<String, String>>> handleOwned(
            SessionNotOwnedException ex, HttpServletRequest request) {
        return ResponseEntity.status(403)
                .body(ApiBody.fail(403, ex.getMessage(), Map.of("errorName", ex.code()),
                        RuntimeWebSupport.requestId(request)));
    }

    @ExceptionHandler(RuntimePortException.class)
    public ResponseEntity<ApiBody<Map<String, String>>> handlePort(
            RuntimePortException ex, HttpServletRequest request) {
        return ResponseEntity.status(403)
                .body(ApiBody.fail(403, ex.getMessage(), Map.of("errorName", ex.code()),
                        RuntimeWebSupport.requestId(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiBody<Map<String, String>>> handleIllegal(
            IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiBody.fail(400, ex.getMessage() == null ? "参数错误" : ex.getMessage(),
                        Map.of("errorName", "BAD_REQUEST"), RuntimeWebSupport.requestId(request)));
    }
}

package com.koujunxin.work.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.koujunxin.common.idempotent.IdempotencyConflictException;
import com.koujunxin.work.catalog.WorkDomainException;
import com.koujunxin.work.catalog.WorkUnpublishedException;

@RestControllerAdvice(basePackages = "com.koujunxin.work.web")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class WorkExceptionAdvice {

    @ExceptionHandler(WorkDomainException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleDomain(WorkDomainException ex, HttpServletRequest request) {
        int code = ex instanceof WorkUnpublishedException
                ? WorkUnpublishedException.CODE
                : ex.httpStatus() >= 400 && ex.httpStatus() < 500 ? ex.httpStatus() : 400;
        // 冻结契约：HTTP 表达协议，body.code 表达业务。1008 用业务码；其余 4xx 协议错误用 HTTP，body.code 同 HTTP。
        int bodyCode = ex instanceof WorkUnpublishedException ? WorkUnpublishedException.CODE : code;
        return ResponseEntity.status(ex.httpStatus())
                .body(ApiEnvelope.error(bodyCode, ex.getMessage(), headerId(request)));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleIdempotency(
            IdempotencyConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(409)
                .body(ApiEnvelope.error(1090, "重复请求与首次参数不一致", headerId(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleIllegal(
            IllegalArgumentException ex, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.badRequest()
                .body(ApiEnvelope.error(400, ex.getMessage(), headerId(request)));
    }

    private static String headerId(HttpServletRequest request) {
        String id = request.getHeader("X-Request-Id");
        return id == null || id.isBlank() ? ApiEnvelope.newId() : id;
    }
}

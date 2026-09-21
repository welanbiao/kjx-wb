package com.koujunxin.member.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.koujunxin.common.idempotent.IdempotencyConflictException;
import com.koujunxin.member.api.MemberException;

@RestControllerAdvice(basePackages = "com.koujunxin.member.web")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MemberExceptionAdvice {

    @ExceptionHandler(MemberException.class)
    public ResponseEntity<ApiBody<Void>> handle(MemberException ex, HttpServletRequest request) {
        String requestId = MemberWebSupport.requestId(request);
        return ResponseEntity.status(ex.httpStatus()).body(ApiBody.fail(ex.bizCode(), ex.getMessage(), requestId));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiBody<Void>> handleIdempotency(IdempotencyConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(409)
                .body(ApiBody.fail(1090, "重复请求与首次参数不一致", MemberWebSupport.requestId(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiBody<Void>> handleIllegal(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiBody.fail(400, ex.getMessage() == null ? "参数错误" : ex.getMessage(),
                        MemberWebSupport.requestId(request)));
    }
}

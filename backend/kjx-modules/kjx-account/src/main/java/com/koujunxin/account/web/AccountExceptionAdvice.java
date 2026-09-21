package com.koujunxin.account.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.koujunxin.account.api.AccountException;

@RestControllerAdvice(basePackages = "com.koujunxin.account")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccountExceptionAdvice {

    @ExceptionHandler(AccountException.class)
    public ResponseEntity<ApiBody<Void>> handle(AccountException ex, HttpServletRequest request, HttpServletResponse response) {
        String requestId = AccountWebSupport.requestId(request);
        response.setHeader(AccountWebSupport.REQUEST_ID, requestId);
        return ResponseEntity.status(ex.httpStatus()).body(ApiBody.fail(ex.bizCode(), ex.getMessage(), requestId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiBody<Void>> handleValid(MethodArgumentNotValidException ex, HttpServletRequest request,
                                                     HttpServletResponse response) {
        String requestId = AccountWebSupport.requestId(request);
        response.setHeader(AccountWebSupport.REQUEST_ID, requestId);
        return ResponseEntity.badRequest().body(ApiBody.fail(400, "参数错误", requestId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiBody<Void>> handleIllegal(IllegalArgumentException ex, HttpServletRequest request,
                                                       HttpServletResponse response) {
        String requestId = AccountWebSupport.requestId(request);
        response.setHeader(AccountWebSupport.REQUEST_ID, requestId);
        return ResponseEntity.badRequest().body(ApiBody.fail(400, "参数错误", requestId));
    }
}

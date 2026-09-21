package com.koujunxin.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.koujunxin.account.api.AccountException;
import com.koujunxin.account.web.AccountWebSupport;
import com.koujunxin.account.web.ApiBody;

@RestControllerAdvice(basePackages = "com.koujunxin.server.auth")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthAdapterExceptionAdvice {

    @ExceptionHandler(AccountException.class)
    public ResponseEntity<ApiBody<Void>> handle(AccountException ex, HttpServletRequest request,
                                                HttpServletResponse response) {
        String requestId = AccountWebSupport.requestId(request);
        response.setHeader(AccountWebSupport.REQUEST_ID, requestId);
        return ResponseEntity.status(ex.httpStatus()).body(ApiBody.fail(ex.bizCode(), ex.getMessage(), requestId));
    }
}

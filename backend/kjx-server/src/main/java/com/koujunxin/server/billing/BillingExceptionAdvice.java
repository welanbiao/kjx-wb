package com.koujunxin.server.billing;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.koujunxin.account.web.AccountWebSupport;
import com.koujunxin.account.web.ApiBody;
import com.koujunxin.billing.api.BillingException;

@RestControllerAdvice(basePackages = "com.koujunxin.server.billing")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BillingExceptionAdvice {

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<ApiBody<Void>> handle(
            BillingException ex, HttpServletRequest request, HttpServletResponse response) {
        String requestId = AccountWebSupport.requestId(request);
        response.setHeader(AccountWebSupport.REQUEST_ID, requestId);
        return ResponseEntity.status(httpStatus(ex))
                .body(ApiBody.fail(ex.code(), safeMessage(ex.getMessage()), requestId));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiBody<Void>> handleBadRequest(
            Exception ex, HttpServletRequest request, HttpServletResponse response) {
        String requestId = AccountWebSupport.requestId(request);
        response.setHeader(AccountWebSupport.REQUEST_ID, requestId);
        return ResponseEntity.badRequest().body(ApiBody.fail(400, safeMessage(ex.getMessage()), requestId));
    }

    static int httpStatus(BillingException ex) {
        return switch (ex.code()) {
            case 1201 -> 402;
            case 1090 -> 409;
            case 1202 -> 502;
            default -> {
                int code = ex.code();
                yield code >= 400 && code <= 599 ? code : 400;
            }
        };
    }

    static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "参数错误";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("sk-")
                || lower.contains("akia")
                || lower.contains("bearer")
                || lower.contains("api_key")
                || lower.contains("secret_key")) {
            return "参数错误";
        }
        return message;
    }
}

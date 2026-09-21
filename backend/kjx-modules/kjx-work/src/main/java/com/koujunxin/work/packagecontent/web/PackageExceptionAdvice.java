package com.koujunxin.work.packagecontent.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.koujunxin.work.catalog.WorkDomainException;
import com.koujunxin.work.packagecontent.WorkPackageException;
import com.koujunxin.work.web.ApiEnvelope;

@RestControllerAdvice(basePackages = {
        "com.koujunxin.work.packagecontent",
        "com.koujunxin.work.content",
        "com.koujunxin.work.asset",
        "com.koujunxin.work.music"
})
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
public class PackageExceptionAdvice {

    @ExceptionHandler(WorkPackageException.class)
    public ResponseEntity<ApiEnvelope<Object>> handlePackage(WorkPackageException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.httpStatus())
                .body(new ApiEnvelope<>(
                        ex.httpStatus() == 404 ? 404 : 400,
                        ex.getMessage(),
                        headerId(request),
                        ex.report()));
    }

    @ExceptionHandler(WorkDomainException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleDomain(WorkDomainException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.httpStatus())
                .body(ApiEnvelope.error(ex.httpStatus(), ex.getMessage(), headerId(request)));
    }

    private static String headerId(HttpServletRequest request) {
        String id = request.getHeader("X-Request-Id");
        return id == null || id.isBlank() ? ApiEnvelope.newId() : id;
    }
}

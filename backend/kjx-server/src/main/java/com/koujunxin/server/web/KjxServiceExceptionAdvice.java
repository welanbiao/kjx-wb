package com.koujunxin.server.web;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将数据权限拒绝映射为 HTTP 403。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class KjxServiceExceptionAdvice {

    @ExceptionHandler(ServiceException.class)
    public R<Void> handle(ServiceException e, HttpServletResponse response) {
        Integer code = e.getCode();
        if (code != null && (code == 401 || code == 403 || code == 404)) {
            response.setStatus(code);
            return R.fail(code, e.getMessage());
        }
        if (code != null) {
            return R.fail(code, e.getMessage());
        }
        return R.fail(e.getMessage());
    }
}

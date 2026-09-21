package org.dromara.common.redis.handler;

import cn.hutool.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Redis异常处理器。
 * 来源：RuoYi-Vue-Plus v5.2.3。KJX 未引入 lock4j，改为捕获通用锁失败。
 *
 * @author AprilWind
 */
@Slf4j
@RestControllerAdvice
public class RedisExceptionHandler {

    /**
     * 分布式锁获取失败（Redisson / 业务包装）
     */
    @ExceptionHandler(IllegalMonitorStateException.class)
    public R<Void> handleLockFailureException(IllegalMonitorStateException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("获取锁失败了'{}',发生锁异常.", requestURI, e);
        return R.fail(HttpStatus.HTTP_UNAVAILABLE, "业务处理中，请稍后再试...");
    }
}

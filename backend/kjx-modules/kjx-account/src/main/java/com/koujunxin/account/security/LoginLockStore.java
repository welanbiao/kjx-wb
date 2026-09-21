package com.koujunxin.account.security;

import java.time.Duration;

/**
 * 登录失败计数。连续失败达阈值后锁定一段时间。
 */
public interface LoginLockStore {

    boolean isLocked(String subject);

    int recordFailure(String subject, int limit, Duration lockTtl);

    void clear(String subject);
}

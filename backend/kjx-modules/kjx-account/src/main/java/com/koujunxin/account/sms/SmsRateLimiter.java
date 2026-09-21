package com.koujunxin.account.sms;

import java.time.Duration;

/**
 * 验证码发送频控：同号 60 秒 1 条、每日 10 条、同 IP 每小时上限。
 */
public interface SmsRateLimiter {

    boolean tryAcquire(String phoneHash, String ipHash, Duration interval, int dailyLimit, int ipHourlyLimit);
}

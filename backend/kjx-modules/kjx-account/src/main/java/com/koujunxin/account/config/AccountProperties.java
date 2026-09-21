package com.koujunxin.account.config;

import java.time.Duration;

/**
 * 账号模块可调参数。失败锁定默认 5 次 / 15 分钟，验证码 5 分钟，注销冷静期 7 天。
 */
public class AccountProperties {

    private Duration smsTtl = Duration.ofMinutes(5);
    private Duration smsInterval = Duration.ofSeconds(60);
    private int smsDailyLimit = 10;
    private int smsIpHourlyLimit = 20;
    private int loginFailLimit = 5;
    private Duration loginLock = Duration.ofMinutes(15);
    private int cancelCoolingDays = 7;
    private long tokenTtlSeconds = 2_592_000L;

    public Duration smsTtl() {
        return smsTtl;
    }

    public void setSmsTtl(Duration smsTtl) {
        this.smsTtl = smsTtl;
    }

    public Duration smsInterval() {
        return smsInterval;
    }

    public void setSmsInterval(Duration smsInterval) {
        this.smsInterval = smsInterval;
    }

    public int smsDailyLimit() {
        return smsDailyLimit;
    }

    public void setSmsDailyLimit(int smsDailyLimit) {
        this.smsDailyLimit = smsDailyLimit;
    }

    public int smsIpHourlyLimit() {
        return smsIpHourlyLimit;
    }

    public void setSmsIpHourlyLimit(int smsIpHourlyLimit) {
        this.smsIpHourlyLimit = smsIpHourlyLimit;
    }

    public int loginFailLimit() {
        return loginFailLimit;
    }

    public void setLoginFailLimit(int loginFailLimit) {
        this.loginFailLimit = loginFailLimit;
    }

    public Duration loginLock() {
        return loginLock;
    }

    public void setLoginLock(Duration loginLock) {
        this.loginLock = loginLock;
    }

    public int cancelCoolingDays() {
        return cancelCoolingDays;
    }

    public void setCancelCoolingDays(int cancelCoolingDays) {
        this.cancelCoolingDays = cancelCoolingDays;
    }

    public long tokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public static AccountProperties defaults() {
        return new AccountProperties();
    }
}

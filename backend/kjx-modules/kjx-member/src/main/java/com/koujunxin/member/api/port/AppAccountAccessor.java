package com.koujunxin.member.api.port;

/**
 * C 端当前账号。生产对齐 {@code StpKit.APP}。
 */
public interface AppAccountAccessor {

    long requireAccountId();
}

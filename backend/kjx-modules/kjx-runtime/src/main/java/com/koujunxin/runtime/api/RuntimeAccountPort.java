package com.koujunxin.runtime.api;

/**
 * C 端当前账号。由 kjx-server 接到 StpKit.APP，本模块不依赖 satoken。
 */
public interface RuntimeAccountPort {

    long requireAccountId();
}

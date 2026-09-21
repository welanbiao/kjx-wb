package com.koujunxin.server.integration;

import org.dromara.common.satoken.utils.StpKit;

import com.koujunxin.runtime.api.RuntimeAccountPort;

/**
 * C 端运行时账号。只读 APP 登录态，不把 token 交给 iframe。
 */
public final class AppRuntimeAccountAdapter implements RuntimeAccountPort {

    @Override
    public long requireAccountId() {
        StpKit.APP.checkLogin();
        return StpKit.APP.getLoginIdAsLong();
    }
}

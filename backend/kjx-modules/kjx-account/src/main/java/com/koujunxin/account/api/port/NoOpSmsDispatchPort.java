package com.koujunxin.account.api.port;

import com.koujunxin.account.api.AccountException;

/**
 * 未装配真实短信适配时 fail-closed：不得返回“已发送”。
 * test/dev 如需放行，必须显式提供 stub/live {@link SmsDispatchPort}。
 */
public final class NoOpSmsDispatchPort implements SmsDispatchPort {

    @Override
    public void dispatch(String scene, String phone, String code) {
        throw new AccountException(500, 500, "短信服务未配置");
    }
}

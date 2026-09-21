package com.koujunxin.server.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.koujunxin.account.api.port.SmsDispatchPort;

/**
 * 仅 test/dev 显式 {@code kjx.sms.mode=stub} 时使用。不调用外部供应商，也不记录手机号或验证码。
 */
public final class StubSmsDispatchPort implements SmsDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(StubSmsDispatchPort.class);

    @Override
    public void dispatch(String scene, String phone, String code) {
        log.info("sms dispatch stubbed scene={}", scene);
    }
}

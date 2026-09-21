package com.koujunxin.account.api.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 未装配 SMS4J 时的占位。不发送、不记录手机号或验证码。
 */
public final class NoOpSmsDispatchPort implements SmsDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpSmsDispatchPort.class);

    @Override
    public void dispatch(String scene, String phone, String code) {
        log.info("sms dispatch skipped (no SmsDispatchPort) scene={}", scene);
    }
}

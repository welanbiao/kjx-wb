package com.koujunxin.account.support;

import java.util.Map;

import com.koujunxin.account.api.port.CaptchaVerificationPort;

public final class PassingCaptchaVerificationPort implements CaptchaVerificationPort {

    @Override
    public boolean verify(String captchaId, Map<String, Object> captchaData) {
        return captchaId != null && captchaId.startsWith("ok-");
    }
}

package com.koujunxin.account.api.port;

import java.util.Map;

/**
 * 默认拒绝所有图形验证码。生产必须提供 tianai-captcha matching 适配。
 */
public final class FailClosedCaptchaVerificationPort implements CaptchaVerificationPort {

    @Override
    public boolean verify(String captchaId, Map<String, Object> captchaData) {
        return false;
    }
}

package com.koujunxin.account.api.port;

import java.util.Map;

/**
 * 图形验证码校验端口。生产由 tianai-captcha {@code matching()} 适配；本模块不绑定具体实现。
 */
@FunctionalInterface
public interface CaptchaVerificationPort {

    /**
     * @return true 表示挑战通过且应视为已消费
     */
    boolean verify(String captchaId, Map<String, Object> captchaData);
}

package com.koujunxin.server.auth;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/**
 * 松手校验通过后的短时凭证，供 send-code / 登录再次消费，避免 matching 只能调用一次。
 */
public final class CaptchaPassStore {

    private static final String KEY = "captcha:pass:";
    private static final long TTL_SECONDS = 120L;

    private final RedissonClient redisson;

    public CaptchaPassStore(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public void markPassed(String captchaId) {
        if (captchaId == null || captchaId.isBlank()) {
            return;
        }
        bucket(captchaId).set("1", TTL_SECONDS, TimeUnit.SECONDS);
    }

    public boolean consumePassed(String captchaId) {
        if (captchaId == null || captchaId.isBlank()) {
            return false;
        }
        String value = bucket(captchaId).getAndDelete();
        return value != null && !value.isBlank();
    }

    private RBucket<String> bucket(String captchaId) {
        return redisson.getBucket(KEY + captchaId, StringCodec.INSTANCE);
    }
}

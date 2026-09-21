package com.koujunxin.account.sms;

import com.koujunxin.account.domain.SmsScene;

/**
 * Redis 短信验证码：只存哈希，单次消费，TTL 过期。
 */
public interface SmsCodeStore {

    void put(SmsScene scene, String phoneHash, String codeHash, java.time.Duration ttl);

    /**
     * 原子读取并删除。匹配则 true；缺失/过期/不匹配均 false（不匹配时也删除，防枚举重试同一码）。
     */
    ConsumeResult consume(SmsScene scene, String phoneHash, String codeHash);

    boolean exists(SmsScene scene, String phoneHash);

    enum ConsumeResult {
        MATCHED,
        MISSING,
        MISMATCH
    }
}

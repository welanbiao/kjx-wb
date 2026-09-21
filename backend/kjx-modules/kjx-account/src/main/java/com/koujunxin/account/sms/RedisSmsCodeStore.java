package com.koujunxin.account.sms;

import java.time.Duration;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import com.koujunxin.account.domain.SmsScene;

public class RedisSmsCodeStore implements SmsCodeStore {

    static final String KEY_PREFIX = "kjx:sms:code:";

    private final RedissonClient redisson;

    public RedisSmsCodeStore(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public void put(SmsScene scene, String phoneHash, String codeHash, Duration ttl) {
        RBucket<String> bucket = redisson.getBucket(key(scene, phoneHash));
        bucket.set(codeHash, ttl);
    }

    @Override
    public ConsumeResult consume(SmsScene scene, String phoneHash, String codeHash) {
        RBucket<String> bucket = redisson.getBucket(key(scene, phoneHash));
        String stored = bucket.getAndDelete();
        if (stored == null) {
            return ConsumeResult.MISSING;
        }
        if (stored.equals(codeHash)) {
            return ConsumeResult.MATCHED;
        }
        return ConsumeResult.MISMATCH;
    }

    @Override
    public boolean exists(SmsScene scene, String phoneHash) {
        return redisson.getBucket(key(scene, phoneHash)).isExists();
    }

    static String key(SmsScene scene, String phoneHash) {
        return KEY_PREFIX + scene.wire() + ":" + phoneHash;
    }
}

package com.koujunxin.server.auth;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import cloud.tianai.captcha.cache.CacheStore;
import cloud.tianai.captcha.common.AnyMap;

/**
 * 基于现有 {@link RedissonClient} 的可共享 CacheStore。多实例必须走 Redis，禁止本机内存冒充生产。
 */
public final class RedissonCaptchaCacheStore implements CacheStore {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final RedissonClient redisson;
    private final ObjectMapper mapper;

    public RedissonCaptchaCacheStore(RedissonClient redisson, ObjectMapper mapper) {
        this.redisson = redisson;
        this.mapper = mapper;
    }

    @Override
    public AnyMap getCache(String key) {
        return read(bucket(key).get());
    }

    @Override
    public AnyMap getAndRemoveCache(String key) {
        return read(bucket(key).getAndDelete());
    }

    @Override
    public boolean setCache(String key, AnyMap data, Long expire, TimeUnit timeUnit) {
        try {
            String json = mapper.writeValueAsString(data);
            long ttl = expire == null ? 0L : expire;
            if (ttl <= 0) {
                bucket(key).set(json);
            } else {
                bucket(key).set(json, ttl, timeUnit == null ? TimeUnit.MILLISECONDS : timeUnit);
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public Long incr(String key, long delta, Long expire, TimeUnit timeUnit) {
        RAtomicLong atomic = redisson.getAtomicLong(key);
        long value = atomic.addAndGet(delta);
        if (expire != null && expire > 0) {
            TimeUnit unit = timeUnit == null ? TimeUnit.MILLISECONDS : timeUnit;
            atomic.expire(Duration.ofMillis(unit.toMillis(expire)));
        }
        return value;
    }

    @Override
    public Long getLong(String key) {
        RAtomicLong atomic = redisson.getAtomicLong(key);
        if (!atomic.isExists()) {
            return null;
        }
        return atomic.get();
    }

    private RBucket<String> bucket(String key) {
        return redisson.getBucket(key, StringCodec.INSTANCE);
    }

    private AnyMap read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return AnyMap.of(mapper.readValue(json, MAP));
        } catch (Exception ex) {
            return null;
        }
    }
}

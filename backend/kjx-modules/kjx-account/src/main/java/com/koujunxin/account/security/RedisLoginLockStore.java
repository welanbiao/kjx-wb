package com.koujunxin.account.security;

import java.time.Duration;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

public class RedisLoginLockStore implements LoginLockStore {

    static final String FAIL_PREFIX = "kjx:login:fail:";
    static final String LOCK_PREFIX = "kjx:login:lock:";

    private final RedissonClient redisson;

    public RedisLoginLockStore(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public boolean isLocked(String subject) {
        return redisson.getBucket(LOCK_PREFIX + subject).isExists();
    }

    @Override
    public int recordFailure(String subject, int limit, Duration lockTtl) {
        RAtomicLong counter = redisson.getAtomicLong(FAIL_PREFIX + subject);
        long count = counter.incrementAndGet();
        if (count == 1) {
            counter.expire(lockTtl);
        }
        if (count >= limit) {
            RBucket<String> lock = redisson.getBucket(LOCK_PREFIX + subject);
            lock.set("1", lockTtl);
        }
        return (int) count;
    }

    @Override
    public void clear(String subject) {
        redisson.getAtomicLong(FAIL_PREFIX + subject).delete();
        redisson.getBucket(LOCK_PREFIX + subject).delete();
    }
}

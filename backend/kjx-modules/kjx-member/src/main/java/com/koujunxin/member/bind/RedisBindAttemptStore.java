package com.koujunxin.member.bind;

import java.time.Duration;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

/**
 * Redis 绑定失败计数。3 次锁定 30 分钟；与 account.bind_fail_count / bind_locked_until 双写。
 */
public class RedisBindAttemptStore {

    private static final String KEY = "kjx:bind:fail:";
    private final RedissonClient redisson;
    private final int failLimit;
    private final Duration lockTtl;

    public RedisBindAttemptStore(RedissonClient redisson, int failLimit, Duration lockTtl) {
        this.redisson = redisson;
        this.failLimit = failLimit;
        this.lockTtl = lockTtl;
    }

    public int increment(long accountId) {
        RAtomicLong counter = redisson.getAtomicLong(KEY + accountId);
        long value = counter.incrementAndGet();
        if (value == 1L) {
            counter.expire(lockTtl);
        }
        return (int) value;
    }

    public int current(long accountId) {
        return (int) redisson.getAtomicLong(KEY + accountId).get();
    }

    public void reset(long accountId) {
        redisson.getAtomicLong(KEY + accountId).delete();
    }

    public boolean locked(int failCount) {
        return failCount >= failLimit;
    }
}

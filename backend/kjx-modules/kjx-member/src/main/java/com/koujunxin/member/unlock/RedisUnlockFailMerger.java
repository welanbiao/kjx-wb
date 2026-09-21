package com.koujunxin.member.unlock;

import java.time.Duration;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

/**
 * 解锁失败 Redis 窗口合并：同一 member/work/reason 在窗口内只落一条 MySQL，避免刷爆 unlock_record。
 */
public class RedisUnlockFailMerger {

    static final String KEY = "kjx:unlock:fail:";
    private final RedissonClient redisson;
    private final Duration window;

    public RedisUnlockFailMerger(RedissonClient redisson, Duration window) {
        this.redisson = redisson;
        this.window = window == null ? Duration.ofSeconds(60) : window;
    }

    /**
     * @return true 时才允许写入 MySQL
     */
    public boolean shouldPersist(long memberId, long workId, String reason) {
        String safeReason = reason == null ? "UNKNOWN" : reason;
        RAtomicLong counter = redisson.getAtomicLong(KEY + memberId + ":" + workId + ":" + safeReason);
        long value = counter.incrementAndGet();
        if (value == 1L) {
            counter.expire(window);
            return true;
        }
        return false;
    }
}

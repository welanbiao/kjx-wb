package com.koujunxin.account.sms;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

public class RedisSmsRateLimiter implements SmsRateLimiter {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final RedissonClient redisson;

    public RedisSmsRateLimiter(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public boolean tryAcquire(String phoneHash, String ipHash, Duration interval, int dailyLimit, int ipHourlyLimit) {
        String intervalKey = "kjx:sms:interval:" + phoneHash;
        RBucket<String> cooldown = redisson.getBucket(intervalKey);
        if (!cooldown.setIfAbsent("1", interval)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(SHANGHAI);
        String dayKey = "kjx:sms:day:" + LocalDate.now(SHANGHAI) + ":" + phoneHash;
        if (!incrementWithin(dayKey, dailyLimit, Duration.ofSeconds(secondsUntilEndOfDay(now)))) {
            cooldown.delete();
            return false;
        }
        String hourKey = "kjx:sms:ip:" + now.getHour() + ":" + ipHash;
        if (!incrementWithin(hourKey, ipHourlyLimit, Duration.ofHours(1))) {
            cooldown.delete();
            return false;
        }
        return true;
    }

    private boolean incrementWithin(String key, int limit, Duration ttl) {
        RAtomicLong counter = redisson.getAtomicLong(key);
        long value = counter.incrementAndGet();
        if (value == 1) {
            counter.expire(ttl);
        }
        if (value > limit) {
            counter.decrementAndGet();
            return false;
        }
        return true;
    }

    private static long secondsUntilEndOfDay(LocalDateTime now) {
        LocalDateTime end = now.toLocalDate().plusDays(1).atStartOfDay();
        return Math.max(1, ChronoUnit.SECONDS.between(now, end));
    }
}

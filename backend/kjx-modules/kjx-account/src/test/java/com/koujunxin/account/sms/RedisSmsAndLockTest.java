package com.koujunxin.account.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.koujunxin.account.domain.SmsScene;
import com.koujunxin.account.security.RedisLoginLockStore;

@Testcontainers(disabledWithoutDocker = true)
class RedisSmsAndLockTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));

    private static RedissonClient client;

    static synchronized RedissonClient redis() {
        if (client == null) {
            Config config = new Config();
            config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
            client = Redisson.create(config);
        }
        return client;
    }

    @BeforeEach
    void flush() {
        redis().getKeys().flushdb();
    }

    @Test
    void codeIsSingleUseThenMissing() {
        RedisSmsCodeStore store = new RedisSmsCodeStore(redis());
        store.put(SmsScene.REGISTER, "hash-a", "code-hash", Duration.ofMinutes(5));
        assertEquals(SmsCodeStore.ConsumeResult.MATCHED, store.consume(SmsScene.REGISTER, "hash-a", "code-hash"));
        assertEquals(SmsCodeStore.ConsumeResult.MISSING, store.consume(SmsScene.REGISTER, "hash-a", "code-hash"));
    }

    @Test
    void mismatchedCodeIsConsumedAndCannotRetry() {
        RedisSmsCodeStore store = new RedisSmsCodeStore(redis());
        store.put(SmsScene.LOGIN, "hash-b", "good", Duration.ofMinutes(5));
        assertEquals(SmsCodeStore.ConsumeResult.MISMATCH, store.consume(SmsScene.LOGIN, "hash-b", "bad"));
        assertEquals(SmsCodeStore.ConsumeResult.MISSING, store.consume(SmsScene.LOGIN, "hash-b", "good"));
    }

    @Test
    void expiredCodeCannotBeConsumed() throws Exception {
        RedisSmsCodeStore store = new RedisSmsCodeStore(redis());
        store.put(SmsScene.LOGIN, "hash-c", "code-hash", Duration.ofSeconds(1));
        Thread.sleep(1300);
        assertEquals(SmsCodeStore.ConsumeResult.MISSING, store.consume(SmsScene.LOGIN, "hash-c", "code-hash"));
    }

    @Test
    void concurrentConsumeOnlyOneMatch() throws Exception {
        RedisSmsCodeStore store = new RedisSmsCodeStore(redis());
        store.put(SmsScene.REGISTER, "hash-d", "code-hash", Duration.ofMinutes(5));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger matched = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (store.consume(SmsScene.REGISTER, "hash-d", "code-hash") == SmsCodeStore.ConsumeResult.MATCHED) {
                    matched.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        assertEquals(1, matched.get());
    }

    @Test
    void rateLimiterIntervalDailyAndIp() {
        RedisSmsRateLimiter limiter = new RedisSmsRateLimiter(redis());
        assertTrue(limiter.tryAcquire("p1", "ip1", Duration.ofMinutes(1), 10, 20));
        assertFalse(limiter.tryAcquire("p1", "ip1", Duration.ofMinutes(1), 10, 20));
        assertTrue(limiter.tryAcquire("p2", "ip1", Duration.ofMinutes(1), 10, 20));
    }

    @Test
    void fiveFailuresLockAccount() {
        RedisLoginLockStore locks = new RedisLoginLockStore(redis());
        String subject = "id:9";
        assertFalse(locks.isLocked(subject));
        for (int i = 1; i <= 4; i++) {
            assertEquals(i, locks.recordFailure(subject, 5, Duration.ofMinutes(15)));
            assertFalse(locks.isLocked(subject));
        }
        assertEquals(5, locks.recordFailure(subject, 5, Duration.ofMinutes(15)));
        assertTrue(locks.isLocked(subject));
        locks.clear(subject);
        assertFalse(locks.isLocked(subject));
    }
}

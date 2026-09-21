package com.koujunxin.server.redis;

import com.koujunxin.server.support.InfraContainers;
import org.dromara.common.redis.utils.QueueUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RateType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
class RedissonCompatibilityIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        InfraContainers.register(registry);
    }

    @Test
    void lockRateLimitAndDelayedQueue() throws Exception {
        RLock lock = RedisUtils.getClient().getLock("kjx:it:lock");
        assertTrue(lock.tryLock(2, 5, TimeUnit.SECONDS));
        try {
            assertTrue(lock.isHeldByCurrentThread());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        String limiterKey = "kjx:it:rate:" + System.nanoTime();
        long first = RedisUtils.rateLimiter(limiterKey, RateType.OVERALL, 1, 30);
        long second = RedisUtils.rateLimiter(limiterKey, RateType.OVERALL, 1, 30);
        assertTrue(first >= 0, "first acquire should succeed, leftover=" + first);
        assertEquals(-1L, second, "second acquire should be limited");

        String queue = "kjx:it:delay:" + System.nanoTime();
        QueueUtils.addDelayedQueueObject(queue, "hello", 200, TimeUnit.MILLISECONDS);
        Thread.sleep(500);
        assertEquals("hello", QueueUtils.getQueueObject(queue));
    }
}

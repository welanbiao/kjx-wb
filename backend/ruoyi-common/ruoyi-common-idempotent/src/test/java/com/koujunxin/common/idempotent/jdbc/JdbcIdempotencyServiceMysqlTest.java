package com.koujunxin.common.idempotent.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.koujunxin.common.idempotent.CanonicalRequestHash;
import com.koujunxin.common.idempotent.IdempotencyBegin;
import com.koujunxin.common.idempotent.IdempotencyCommand;
import com.koujunxin.common.idempotent.IdempotencyConflictException;
import com.koujunxin.common.idempotent.IdempotencyResult;

class JdbcIdempotencyServiceMysqlTest extends MysqlFlywaySupport {

    private JdbcIdempotencyService service;

    @BeforeEach
    void setUp() {
        ensureMigrated();
        truncateGuard();
        service = new JdbcIdempotencyService(jdbc());
    }

    @Test
    void firstAttemptThenSameHashReplays() {
        IdempotencyCommand command = command("unlock", 11L, "key-same", Map.of("workCode", "a"));
        IdempotencyResult first = tx().execute(status ->
                service.execute(command, () -> IdempotencyResult.of("OK", "{\"id\":1}")));
        IdempotencyResult replay = tx().execute(status ->
                service.execute(command, () -> {
                    throw new IllegalStateException("must not re-run");
                }));
        assertThat(first.resultCode()).isEqualTo("OK");
        assertThat(replay.resultBodyJson()).contains("id").contains("1");
        assertThat(countRows()).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentHashConflicts() {
        IdempotencyCommand first = command("unlock", 12L, "key-conflict", Map.of("workCode", "a"));
        tx().execute(status -> service.execute(first, () -> IdempotencyResult.of("OK", "{}")));
        IdempotencyCommand second = command("unlock", 12L, "key-conflict", Map.of("workCode", "b"));
        assertThatThrownBy(() -> tx().execute(status ->
                service.execute(second, () -> IdempotencyResult.of("OK", "{}"))))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting(ex -> ((IdempotencyConflictException) ex).businessCode())
                .isEqualTo(1090);
    }

    @Test
    void sameKeyDifferentRefAreIndependent() {
        IdempotencyCommand one = command("unlock", 1L, "shared-key", Map.of("n", 1));
        IdempotencyCommand two = command("unlock", 2L, "shared-key", Map.of("n", 1));
        tx().execute(status -> service.execute(one, () -> IdempotencyResult.of("A", "{\"r\":1}")));
        tx().execute(status -> service.execute(two, () -> IdempotencyResult.of("B", "{\"r\":2}")));
        assertThat(countRows()).isEqualTo(2);
    }

    @Test
    void rollbackLeavesNoSuccessfulGuard() {
        IdempotencyCommand command = command("binding", 99L, "key-rollback", Map.of("accountId", 99));
        assertThatThrownBy(() -> tx().execute(status -> {
            service.begin(command);
            throw new IllegalStateException("business boom");
        })).hasMessageContaining("business boom");
        assertThat(countRows()).isZero();
    }

    @Test
    void beginRequiresTransaction() {
        IdempotencyCommand command = command("pay_callback", 3L, "key-tx", Map.of("id", 3));
        assertThatThrownBy(() -> service.begin(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }

    @Test
    void concurrentSameHashOnlyRunsOnce() throws Exception {
        IdempotencyCommand command = command("unlock", 77L, "key-concurrent", Map.of("work", 77));
        AtomicInteger executions = new AtomicInteger();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IdempotencyResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            TransactionTemplate isolated = new TransactionTemplate(txManager());
            isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            futures.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return isolated.execute(status -> service.execute(command, () -> {
                    executions.incrementAndGet();
                    return IdempotencyResult.of("OK", "{\"n\":1}");
                }));
            }));
        }
        start.countDown();
        for (Future<IdempotencyResult> future : futures) {
            assertThat(future.get(30, TimeUnit.SECONDS).resultCode()).isEqualTo("OK");
        }
        pool.shutdownNow();
        assertThat(executions.get()).isEqualTo(1);
        assertThat(countRows()).isEqualTo(1);
    }

    @Test
    void firstAttemptIsVisibleAsProcessingBeforeComplete() {
        IdempotencyCommand command = command("unlock", 5L, "key-processing", Map.of("x", 1));
        IdempotencyBegin begin = tx().execute(status -> service.begin(command));
        assertThat(begin.firstAttempt()).isTrue();
        String status = jdbc().queryForObject(
                "SELECT status FROM kjx_idempotency_guard WHERE scene=? AND ref_id=? AND idempotency_key=?",
                String.class, command.scene(), command.refId(), command.idempotencyKey());
        assertThat(status).isEqualTo("PROCESSING");
    }

    private IdempotencyCommand command(String scene, long refId, String key, Map<String, Object> payload) {
        return new IdempotencyCommand(
                scene,
                refId,
                key,
                1001L,
                CanonicalRequestHash.sha256Hex(payload),
                Duration.ofHours(24));
    }

    private long countRows() {
        Long count = jdbc().queryForObject("SELECT COUNT(*) FROM kjx_idempotency_guard", Long.class);
        return count == null ? 0 : count;
    }
}

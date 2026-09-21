package com.koujunxin.notification.outbox.jdbc;

import java.time.Duration;
import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import com.koujunxin.notification.outbox.OutboxProperties;
import com.koujunxin.notification.outbox.OutboxPublisher;
import com.koujunxin.notification.outbox.OutboxRecord;
import com.koujunxin.notification.outbox.OutboxRepository;
import com.koujunxin.notification.outbox.OutboxWorker;
import com.koujunxin.notification.outbox.support.ErrorDigest;
import com.koujunxin.notification.outbox.support.RetryBackoff;

/**
 * 至少一次投递：先 claim（PROCESSING + next_retry_at 作为租约到期），事务外 publish，成功后再标 PUBLISHED。
 */
public class JdbcOutboxWorker implements OutboxWorker {

    private final OutboxRepository repository;
    private final OutboxPublisher publisher;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties properties;

    public JdbcOutboxWorker(
            OutboxRepository repository,
            OutboxPublisher publisher,
            TransactionTemplate transactionTemplate,
            OutboxProperties properties) {
        this.repository = repository;
        this.publisher = publisher;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    @Override
    public int drain() {
        List<OutboxRecord> batch = transactionTemplate.execute(status ->
                repository.claim(properties.batchSize(), properties.lease()));
        if (batch == null || batch.isEmpty()) {
            return 0;
        }
        int published = 0;
        for (OutboxRecord record : batch) {
            try {
                publisher.publish(record);
                transactionTemplate.executeWithoutResult(status -> repository.markPublished(record.eventId()));
                published++;
            } catch (Exception ex) {
                String digest = ErrorDigest.of(ex);
                int attempts = record.retryCount() + 1;
                transactionTemplate.executeWithoutResult(status -> {
                    if (attempts >= properties.maxAttempts()) {
                        repository.markDead(record.eventId(), digest);
                    } else {
                        Duration delay = RetryBackoff.delay(attempts, properties.maxBackoff());
                        repository.markRetry(record.eventId(), digest, delay);
                    }
                });
            }
        }
        return published;
    }
}

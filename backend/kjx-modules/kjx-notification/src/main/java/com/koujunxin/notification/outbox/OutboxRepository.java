package com.koujunxin.notification.outbox;

import java.time.Duration;
import java.util.List;

/**
 * Outbox 仓储：领取、发布、重试、DEAD、租约回收。
 */
public interface OutboxRepository {

    List<OutboxRecord> claim(int batchSize, Duration lease);

    void markPublished(String eventId);

    void markRetry(String eventId, String lastError, Duration delay);

    void markDead(String eventId, String lastError);

    int recoverExpiredLeases();

    int recoverDead(String eventId);
}

package com.koujunxin.notification.outbox.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.koujunxin.notification.outbox.OutboxPublisher;
import com.koujunxin.notification.outbox.OutboxRecord;
import com.koujunxin.notification.outbox.consume.ConsumerIdempotencyPort;

/**
 * 测试消费者：按 event_id + channel 幂等，记录投递次数与成功消费结果。
 */
public class RecordingTestConsumer implements OutboxPublisher {

    public static final String CHANNEL = "TEST";

    private final ConsumerIdempotencyPort idempotency;
    private final AtomicInteger deliveries = new AtomicInteger();
    private final List<OutboxRecord> consumed = new CopyOnWriteArrayList<>();

    public RecordingTestConsumer(ConsumerIdempotencyPort idempotency) {
        this.idempotency = idempotency;
    }

    @Override
    public void publish(OutboxRecord record) {
        deliveries.incrementAndGet();
        if (idempotency.alreadyCompleted(record.eventId(), CHANNEL)) {
            return;
        }
        if (!idempotency.tryBegin(record.eventId(), CHANNEL)) {
            if (idempotency.alreadyCompleted(record.eventId(), CHANNEL)) {
                return;
            }
            throw new IllegalStateException("consumer in progress: " + record.eventId());
        }
        try {
            consumed.add(record);
            idempotency.complete(record.eventId(), CHANNEL);
        } catch (RuntimeException ex) {
            idempotency.abandon(record.eventId(), CHANNEL);
            throw ex;
        }
    }

    public int deliveryCount() {
        return deliveries.get();
    }

    public List<OutboxRecord> consumed() {
        return List.copyOf(consumed);
    }
}

package com.koujunxin.notification.outbox.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.koujunxin.notification.outbox.OutboxEnvelope;
import com.koujunxin.notification.outbox.OutboxPublisher;
import com.koujunxin.notification.outbox.consume.ConsumerIdempotencyPort;
import com.koujunxin.notification.outbox.consume.ExternalSideEffectKeys;

/**
 * 测试消费者：按 event_id + channel 幂等，记录投递次数与成功消费的完整信封。
 * 模拟短信通道时把 eventId 传给提供方幂等键；外部 SMS 仍是至少一次。
 */
public class RecordingTestConsumer implements OutboxPublisher {

    public static final String CHANNEL = "TEST";

    private final ConsumerIdempotencyPort idempotency;
    private final AtomicInteger deliveries = new AtomicInteger();
    private final List<OutboxEnvelope> consumed = new CopyOnWriteArrayList<>();
    private final List<String> smsProviderKeys = new CopyOnWriteArrayList<>();

    public RecordingTestConsumer(ConsumerIdempotencyPort idempotency) {
        this.idempotency = idempotency;
    }

    @Override
    public void publish(OutboxEnvelope envelope) {
        deliveries.incrementAndGet();
        if (idempotency.alreadyCompleted(envelope.eventId(), CHANNEL)) {
            return;
        }
        if (!idempotency.tryBegin(envelope.eventId(), CHANNEL)) {
            if (idempotency.alreadyCompleted(envelope.eventId(), CHANNEL)) {
                return;
            }
            throw new IllegalStateException("consumer in progress: " + envelope.eventId());
        }
        try {
            smsProviderKeys.add(ExternalSideEffectKeys.smsProviderEventId(envelope.eventId()));
            consumed.add(envelope);
            idempotency.complete(envelope.eventId(), CHANNEL);
        } catch (RuntimeException ex) {
            idempotency.abandon(envelope.eventId(), CHANNEL);
            throw ex;
        }
    }

    public int deliveryCount() {
        return deliveries.get();
    }

    public List<OutboxEnvelope> consumed() {
        return List.copyOf(consumed);
    }

    public List<String> smsProviderKeys() {
        return List.copyOf(smsProviderKeys);
    }
}

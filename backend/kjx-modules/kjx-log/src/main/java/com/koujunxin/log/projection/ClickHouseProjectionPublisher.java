package com.koujunxin.log.projection;

import com.koujunxin.log.api.port.ObservabilityMetricsPort;
import com.koujunxin.log.redact.LogRedactor;
import com.koujunxin.notification.outbox.OutboxEnvelope;
import com.koujunxin.notification.outbox.OutboxPublisher;
import com.koujunxin.notification.outbox.consume.ConsumerIdempotencyPort;

/**
 * 消费分析 Outbox：按 eventId + channel 幂等写入 ClickHouse。
 * 非分析事件直接忽略，便于作为缺省 {@link OutboxPublisher} 而不抢走通知投递（有业务 Publisher 时本 bean 不创建）。
 */
public final class ClickHouseProjectionPublisher implements OutboxPublisher {

    private final ConsumerIdempotencyPort idempotency;
    private final ClickHouseProjectionSink sink;
    private final ObservabilityMetricsPort metrics;
    private final LogRedactor redactor;

    public ClickHouseProjectionPublisher(
            ConsumerIdempotencyPort idempotency,
            ClickHouseProjectionSink sink,
            ObservabilityMetricsPort metrics,
            LogRedactor redactor) {
        this.idempotency = idempotency;
        this.sink = sink;
        this.metrics = metrics;
        this.redactor = redactor;
    }

    @Override
    public void publish(OutboxEnvelope envelope) throws Exception {
        if (envelope == null || !ExternalProjectionKeys.isAnalytics(envelope.eventType())) {
            return;
        }
        String eventId = ExternalProjectionKeys.clickHouseEventId(envelope.eventId());
        String channel = ExternalProjectionKeys.channelForEventType(envelope.eventType());
        if (idempotency.alreadyCompleted(eventId, channel)) {
            return;
        }
        if (!idempotency.tryBegin(eventId, channel)) {
            return;
        }
        try {
            sink.insert(envelope);
            idempotency.complete(eventId, channel);
        } catch (Exception ex) {
            idempotency.abandon(eventId, channel);
            metrics.recordProjectionError();
            throw new IllegalStateException(redactor.redactThrowable(ex), ex);
        }
    }
}

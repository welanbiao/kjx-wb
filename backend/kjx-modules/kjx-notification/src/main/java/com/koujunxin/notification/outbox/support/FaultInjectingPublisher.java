package com.koujunxin.notification.outbox.support;

import java.util.concurrent.atomic.AtomicInteger;

import com.koujunxin.notification.outbox.OutboxEnvelope;
import com.koujunxin.notification.outbox.OutboxPublisher;

/**
 * 故障注入 Publisher：先失败 N 次，再委托真实投递。
 */
public class FaultInjectingPublisher implements OutboxPublisher {

    private final OutboxPublisher delegate;
    private final AtomicInteger remainingFailures;

    public FaultInjectingPublisher(OutboxPublisher delegate, int failures) {
        this.delegate = delegate;
        this.remainingFailures = new AtomicInteger(Math.max(0, failures));
    }

    @Override
    public void publish(OutboxEnvelope envelope) throws Exception {
        if (remainingFailures.getAndDecrement() > 0) {
            throw new IllegalStateException("injected publish failure for event " + envelope.eventId());
        }
        delegate.publish(envelope);
    }

    public int remainingFailures() {
        return Math.max(0, remainingFailures.get());
    }
}

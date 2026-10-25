package com.koujunxin.server.integration;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import com.koujunxin.billing.api.BillingOutboxPort;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxWriter;

/**
 * 事务内把账本 Outbox 端口委托给通知模块 {@link OutboxWriter}。payload 原样透传。
 */
final class BillingOutboxAdapter implements BillingOutboxPort {

    private static final Logger log = LoggerFactory.getLogger(BillingOutboxAdapter.class);

    private final OutboxWriter outbox;
    private final ObjectProvider<RequestContextPort> contexts;

    BillingOutboxAdapter(OutboxWriter outbox, ObjectProvider<RequestContextPort> contexts) {
        this.outbox = outbox;
        this.contexts = contexts;
    }

    @Override
    public void append(
            String aggregateType,
            String aggregateId,
            String eventType,
            String idempotencyKey,
            Map<String, Object> payload) {
        RequestContextPort.RequestContextSnapshot snapshot = snapshot();
        outbox.append(new OutboxAppendCommand(
                null,
                aggregateType,
                aggregateId,
                eventType,
                schemaVersion(payload),
                Instant.now(),
                snapshot == null ? null : blankToNull(snapshot.traceId()),
                snapshot == null ? null : blankToNull(snapshot.requestId()),
                idempotencyKey,
                payload));
        log.debug("billing outbox appended aggregateType={} aggregateId={} eventType={}",
                aggregateType, aggregateId, eventType);
    }

    private RequestContextPort.RequestContextSnapshot snapshot() {
        RequestContextPort port = contexts == null ? null : contexts.getIfAvailable();
        if (port == null) {
            return null;
        }
        try {
            return port.current();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String schemaVersion(Map<String, Object> payload) {
        if (payload != null) {
            Object version = payload.get("schemaVersion");
            if (version instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        return OutboxAppendCommand.CURRENT_SCHEMA;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

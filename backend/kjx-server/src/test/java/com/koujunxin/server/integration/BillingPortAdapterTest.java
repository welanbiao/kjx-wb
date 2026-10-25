package com.koujunxin.server.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.koujunxin.log.api.port.BillingLogWritePort;
import com.koujunxin.log.api.port.BillingLogWritePort.BillingLogCommand;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxWriter;

@ExtendWith(MockitoExtension.class)
class BillingPortAdapterTest {

    @Mock
    private ObjectProvider<RequestContextPort> contexts;

    @Test
    void outboxPassesPayloadThroughAndFillsTraceFromContext() {
        when(contexts.getIfAvailable()).thenReturn(context("trace-1", "req-1"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("memberId", 3L);
        payload.put("amount", "1.000000");
        RecordingOutbox outbox = new RecordingOutbox();
        new BillingOutboxAdapter(outbox, contexts)
                .append("billing", "41", "billing.frozen", "idem-1", payload);
        OutboxAppendCommand command = outbox.last;
        assertSame(payload, command.payload());
        assertEquals("billing", command.aggregateType());
        assertEquals("41", command.aggregateId());
        assertEquals("billing.frozen", command.eventType());
        assertEquals("idem-1", command.idempotencyKey());
        assertEquals("1.0", command.schemaVersion());
        assertEquals("trace-1", command.traceId());
        assertEquals("req-1", command.requestId());
    }

    @Test
    void outboxLeavesTraceEmptyWhenContextMissing() {
        when(contexts.getIfAvailable()).thenReturn(null);
        Map<String, Object> payload = Map.of("schemaVersion", "1.0", "memberId", 1L);
        RecordingOutbox outbox = new RecordingOutbox();
        new BillingOutboxAdapter(outbox, contexts)
                .append("billing", "1", "billing.released", "k", payload);
        assertNull(outbox.last.traceId());
        assertNull(outbox.last.requestId());
    }

    @Test
    void auditPayloadOnlyHasBizTypeAndDirection() {
        when(contexts.getIfAvailable()).thenReturn(context("ctx-trace", "ctx-req"));
        RecordingLogs logs = new RecordingLogs();
        new BillingAuditAdapter(logs, contexts)
                .append(3L, "FREEZE", "HOLD", new BigDecimal("1.000000"), "idem-1", "", "");
        BillingLogCommand command = logs.last;
        assertEquals(Map.of("bizType", "FREEZE", "direction", "HOLD"), command.payload());
        assertEquals("ctx-trace", command.traceId());
        assertEquals("ctx-req", command.requestId());
        assertTrue(command.logId() != null && !command.logId().isBlank());
        assertEquals(3L, command.memberId());
    }

    private static RequestContextPort context(String traceId, String requestId) {
        return new RequestContextPort() {
            @Override
            public RequestContextSnapshot current() {
                return RequestContextSnapshot.of(requestId, traceId);
            }

            @Override
            public void bind(RequestContextSnapshot snapshot) {
            }

            @Override
            public void clear() {
            }
        };
    }

    private static final class RecordingOutbox implements OutboxWriter {
        private OutboxAppendCommand last;

        @Override
        public String append(OutboxAppendCommand command) {
            last = command;
            return command.eventId();
        }
    }

    private static final class RecordingLogs implements BillingLogWritePort {
        private BillingLogCommand last;

        @Override
        public void append(BillingLogCommand command) {
            last = command;
        }
    }
}

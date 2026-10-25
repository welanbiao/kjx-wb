package com.koujunxin.server.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;

import com.koujunxin.billing.api.BillingAuditPort;
import com.koujunxin.log.api.port.BillingLogWritePort;
import com.koujunxin.log.api.port.BillingLogWritePort.BillingLogCommand;
import com.koujunxin.log.api.port.RequestContextPort;

/**
 * 账本审计委托 {@link BillingLogWritePort}。payload 只含 bizType/direction。
 */
final class BillingAuditAdapter implements BillingAuditPort {

    private final BillingLogWritePort logs;
    private final ObjectProvider<RequestContextPort> contexts;

    BillingAuditAdapter(BillingLogWritePort logs, ObjectProvider<RequestContextPort> contexts) {
        this.logs = logs;
        this.contexts = contexts;
    }

    @Override
    public void append(
            long memberId,
            String bizType,
            String direction,
            BigDecimal amount,
            String idempotencyKey,
            String traceId,
            String requestId) {
        RequestContextPort.RequestContextSnapshot snapshot = snapshot();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bizType", bizType);
        payload.put("direction", direction);
        logs.append(new BillingLogCommand(
                UUID.randomUUID().toString(),
                Instant.now(),
                firstNonBlank(traceId, snapshot == null ? null : snapshot.traceId()),
                firstNonBlank(requestId, snapshot == null ? null : snapshot.requestId()),
                memberId,
                bizType,
                direction,
                amount,
                idempotencyKey,
                payload));
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

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return UUID.randomUUID().toString();
    }
}

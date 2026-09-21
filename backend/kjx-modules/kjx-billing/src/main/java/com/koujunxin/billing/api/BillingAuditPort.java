package com.koujunxin.billing.api;

import java.math.BigDecimal;

public interface BillingAuditPort {

    void append(long memberId, String bizType, String direction, BigDecimal amount, String idempotencyKey, String traceId, String requestId);
}

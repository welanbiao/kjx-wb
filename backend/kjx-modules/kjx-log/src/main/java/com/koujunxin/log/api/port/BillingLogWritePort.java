package com.koujunxin.log.api.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * MySQL BILLING 日志写入。与扣费/账本同事务；不访问 kjx-billing 表。
 */
public interface BillingLogWritePort {

    void append(BillingLogCommand command);

    record BillingLogCommand(
            String logId,
            Instant ts,
            String traceId,
            String requestId,
            Long memberId,
            String bizType,
            String direction,
            BigDecimal amount,
            String idempotencyKey,
            Map<String, Object> payload
    ) {
    }
}

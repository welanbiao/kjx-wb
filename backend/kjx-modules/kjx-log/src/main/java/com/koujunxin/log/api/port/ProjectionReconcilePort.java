package com.koujunxin.log.api.port;

import java.time.Instant;

/**
 * 投影对账：比较 Outbox 已发布数、消费回执与 ClickHouse 行数。允许最终一致。
 */
public interface ProjectionReconcilePort {

    ReconcileResult reconcile(String category, Instant windowStart, Instant windowEnd);

    record ReconcileResult(
            String category,
            long mysqlPublished,
            long receiptDone,
            Long clickhouseRows,
            String status,
            String detail
    ) {
        public boolean matched() {
            return "MATCHED".equals(status);
        }
    }
}

package com.koujunxin.billing.api;

/**
 * 事务提交后的分析投影。失败不得影响已提交账本。
 */
public interface BillingProjection {

    void publish(String idempotencyKey);

    static BillingProjection noop() {
        return idempotencyKey -> {
        };
    }
}

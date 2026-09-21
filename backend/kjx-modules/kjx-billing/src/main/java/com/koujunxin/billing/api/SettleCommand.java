package com.koujunxin.billing.api;

import com.koujunxin.billing.ledger.Money;

public record SettleCommand(
        long holdId,
        Money actual,
        String idempotencyKey,
        String payloadHash,
        int inputTokens,
        int outputTokens,
        String model,
        BillingActor actor) {
}

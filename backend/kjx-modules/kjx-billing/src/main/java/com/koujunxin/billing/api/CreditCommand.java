package com.koujunxin.billing.api;

import com.koujunxin.billing.ledger.Money;

public record CreditCommand(
        long memberId,
        Money amount,
        String idempotencyKey,
        String payloadHash,
        String provider,
        String outTradeNo,
        String providerTradeNo,
        String reason,
        BillingActor actor) {
}

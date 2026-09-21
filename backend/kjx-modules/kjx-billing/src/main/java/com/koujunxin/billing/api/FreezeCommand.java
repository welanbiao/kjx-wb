package com.koujunxin.billing.api;

import com.koujunxin.billing.ledger.Money;

public record FreezeCommand(
        long memberId,
        Money amount,
        String idempotencyKey,
        String payloadHash,
        String reason,
        Long workId,
        BillingActor actor) {
}

package com.koujunxin.billing.api;

public record ReleaseCommand(long holdId, String reason, BillingActor actor) {
}

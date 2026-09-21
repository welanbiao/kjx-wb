package com.koujunxin.billing.api;

import java.util.Map;

public interface BillingOutboxPort {

    void append(String aggregateType, String aggregateId, String eventType, String idempotencyKey, Map<String, Object> payload);
}

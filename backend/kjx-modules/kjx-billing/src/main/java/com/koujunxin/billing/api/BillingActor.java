package com.koujunxin.billing.api;

public record BillingActor(long deptId, boolean ops, String traceId, String requestId) {

    public BillingActor {
        if (deptId <= 0) {
            throw BillingException.rejected("部门无效");
        }
        traceId = blank(traceId, "trace-missing");
        requestId = blank(requestId, "request-missing");
    }

    private static String blank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}

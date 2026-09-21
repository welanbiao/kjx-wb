package com.koujunxin.billing.api;

public final class BillingException extends RuntimeException {

    private final String name;
    private final int code;

    public BillingException(String name, int code, String message) {
        super(message);
        this.name = name;
        this.code = code;
    }

    public static BillingException conflict() {
        return new BillingException("IDEMPOTENCY_CONFLICT", 1090, "重复请求与首次参数不一致");
    }

    public static BillingException insufficient() {
        return new BillingException("INSUFFICIENT_BALANCE", 1201, "余额不足");
    }

    public static BillingException forbidden() {
        return new BillingException("FORBIDDEN", 403, "无权操作该部门账本");
    }

    public static BillingException byokFailed() {
        return new BillingException("BYOK_FAILED", 1202, "上游调用失败，未扣平台余额");
    }

    public static BillingException rejected(String message) {
        return new BillingException("BILLING_REJECTED", 400, message);
    }

    public String name() {
        return name;
    }

    public int code() {
        return code;
    }
}

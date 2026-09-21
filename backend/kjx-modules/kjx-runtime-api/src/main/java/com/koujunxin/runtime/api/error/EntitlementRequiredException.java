package com.koujunxin.runtime.api.error;

/**
 * 缺少 entitlement 事实。不得由新解锁策略替代本异常。
 */
public class EntitlementRequiredException extends RuntimePortException {

    public EntitlementRequiredException(long accountId, String workCode) {
        super("ENTITLEMENT_REQUIRED", "entitlement required");
        this.accountId = accountId;
        this.workCode = workCode;
    }

    private final long accountId;
    private final String workCode;

    public long accountId() {
        return accountId;
    }

    public String workCode() {
        return workCode;
    }
}

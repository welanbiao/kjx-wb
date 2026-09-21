package com.koujunxin.member.api.port;

/**
 * 解锁同事务故障注入点。生产为空操作；IT 证明 DuplicateKey / AUDIT / Outbox 失败必须回滚 CAS。
 */
public interface UnlockTransactionHook {

    default void afterCas() {
    }

    default void beforeEntitlementInsert() {
    }

    default void afterEntitlementInserted() {
    }

    default void afterUnlockRecordInserted() {
    }

    default void afterAudit() {
    }

    default void afterOutbox() {
    }
}

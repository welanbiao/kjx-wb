package com.koujunxin.member.api.port;

/**
 * 审核同事务故障注入点。生产为空操作；IT 用于证明任一步失败全部回滚。
 */
public interface ReviewTransactionHook {

    default void afterBindingMatched() {
    }

    default void afterMemberBound() {
    }

    default void afterAccountUpdated() {
    }

    default void afterAudit() {
    }

    default void afterOutbox() {
    }
}

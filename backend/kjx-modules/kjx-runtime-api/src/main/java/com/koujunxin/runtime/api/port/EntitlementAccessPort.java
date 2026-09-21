package com.koujunxin.runtime.api.port;

/**
 * 已有权益访问端口。只判断 entitlement 事实，不得复用新解锁策略。
 */
public interface EntitlementAccessPort {

    void requireEntitlement(long accountId, String workCode);
}

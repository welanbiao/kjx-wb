package com.koujunxin.member.entitlement;

import java.util.Collection;
import java.util.Set;

import com.koujunxin.member.jdbc.EntitlementRepository;
import com.koujunxin.work.port.EntitlementQueryPort;

/**
 * 作品列表/详情权益短路。只读事实表，不复制可见性/灰度算法。
 */
public class JdbcEntitlementQueryPort implements EntitlementQueryPort {

    private final EntitlementRepository entitlements;

    public JdbcEntitlementQueryPort(EntitlementRepository entitlements) {
        this.entitlements = entitlements;
    }

    @Override
    public boolean hasEntitlement(long accountId, long workId) {
        return entitlements.hasAccessible(accountId, workId);
    }

    @Override
    public Set<Long> findEntitledWorkIds(long accountId) {
        return entitlements.findWorkIdsByAccount(accountId);
    }

    @Override
    public Set<Long> findEntitledWorkIds(long accountId, Collection<Long> workIds) {
        return entitlements.findWorkIdsByAccount(accountId, workIds);
    }
}

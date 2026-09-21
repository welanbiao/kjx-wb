package com.koujunxin.work.port;

import java.util.Set;

/**
 * 缺省桩：无任何权益。生产由 WP-13 覆盖。
 */
public final class EmptyEntitlementQueryPort implements EntitlementQueryPort {

    @Override
    public boolean hasEntitlement(long accountId, long workId) {
        return false;
    }

    @Override
    public Set<Long> findEntitledWorkIds(long accountId) {
        return Set.of();
    }
}

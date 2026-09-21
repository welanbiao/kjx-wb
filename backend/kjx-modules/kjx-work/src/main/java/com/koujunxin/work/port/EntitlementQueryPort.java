package com.koujunxin.work.port;

import java.util.Collection;
import java.util.Set;

/**
 * 权益只读窄端口。由 WP-13 实现；本模块不得读取 member Mapper / entitlement 表。
 */
public interface EntitlementQueryPort {

    boolean hasEntitlement(long accountId, long workId);

    Set<Long> findEntitledWorkIds(long accountId);

    default Set<Long> findEntitledWorkIds(long accountId, Collection<Long> workIds) {
        if (workIds == null || workIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> entitled = findEntitledWorkIds(accountId);
        return entitled.stream().filter(workIds::contains).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}

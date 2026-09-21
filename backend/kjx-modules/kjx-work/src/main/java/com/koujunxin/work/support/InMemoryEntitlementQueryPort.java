package com.koujunxin.work.support;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.koujunxin.work.port.EntitlementQueryPort;

/**
 * WP-13/WP-20 测试桩。生产 AutoConfiguration 不注册本类。
 */
public final class InMemoryEntitlementQueryPort implements EntitlementQueryPort {

    private final Set<String> keys = ConcurrentHashMap.newKeySet();

    public void grant(long accountId, long workId) {
        keys.add(key(accountId, workId));
    }

    public void revoke(long accountId, long workId) {
        keys.remove(key(accountId, workId));
    }

    public void clear() {
        keys.clear();
    }

    @Override
    public boolean hasEntitlement(long accountId, long workId) {
        return keys.contains(key(accountId, workId));
    }

    @Override
    public Set<Long> findEntitledWorkIds(long accountId) {
        String prefix = accountId + ":";
        return keys.stream()
                .filter(k -> k.startsWith(prefix))
                .map(k -> Long.parseLong(k.substring(prefix.length())))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String key(long accountId, long workId) {
        return accountId + ":" + workId;
    }
}

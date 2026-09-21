package com.koujunxin.member.port;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.koujunxin.member.api.port.HostDeptPort;

public final class InMemoryHostDeptPort implements HostDeptPort {

    private final AtomicLong seq = new AtomicLong(10_000);
    private final java.util.concurrent.ConcurrentHashMap<Long, String> names = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public long createDepartment(String hostName, Long parentDeptId, long adminUserId) {
        long id = seq.incrementAndGet();
        names.put(id, hostName);
        return id;
    }

    @Override
    public void rename(long deptId, String hostName) {
        names.put(deptId, hostName);
    }

    @Override
    public Optional<String> findName(long deptId) {
        return Optional.ofNullable(names.get(deptId));
    }

    public void put(long deptId, String name) {
        names.put(deptId, name);
        seq.updateAndGet(current -> Math.max(current, deptId));
    }
}

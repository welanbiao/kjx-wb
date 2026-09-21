package com.koujunxin.member.support;

import java.util.Set;

import com.koujunxin.member.api.MemberFailures;
import com.koujunxin.member.api.port.DeptAccessPort;

public final class FixedDeptAccessPort implements DeptAccessPort {

    private final long actorId;
    private final long deptId;
    private final boolean ops;
    private final Set<Long> allowed;

    public FixedDeptAccessPort(long actorId, long deptId, boolean ops, Set<Long> allowed) {
        this.actorId = actorId;
        this.deptId = deptId;
        this.ops = ops;
        this.allowed = allowed;
    }

    public static FixedDeptAccessPort host(long actorId, long deptId) {
        return new FixedDeptAccessPort(actorId, deptId, false, Set.of(deptId));
    }

    public static FixedDeptAccessPort ops(long actorId) {
        return new FixedDeptAccessPort(actorId, 0, true, Set.of());
    }

    public static FixedDeptAccessPort opsLimited(long actorId, long... deptIds) {
        java.util.HashSet<Long> allowed = new java.util.HashSet<>();
        for (long deptId : deptIds) {
            allowed.add(deptId);
        }
        return new FixedDeptAccessPort(actorId, 0, true, Set.copyOf(allowed));
    }

    @Override
    public long requireActorId() {
        return actorId;
    }

    @Override
    public long requireDeptId() {
        if (deptId <= 0 && !ops) {
            throw MemberFailures.forbidden();
        }
        return deptId;
    }

    @Override
    public void requireAccess(long resourceDeptId) {
        if (ops) {
            if (!allowed.isEmpty() && !allowed.contains(resourceDeptId)) {
                throw MemberFailures.forbidden();
            }
            return;
        }
        if (!allowed.contains(resourceDeptId)) {
            throw MemberFailures.forbidden();
        }
    }

    @Override
    public boolean ops() {
        return ops;
    }

    @Override
    public boolean host() {
        return !ops;
    }
}

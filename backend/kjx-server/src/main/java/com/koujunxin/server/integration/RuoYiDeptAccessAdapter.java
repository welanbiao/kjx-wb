package com.koujunxin.server.integration;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;

import com.koujunxin.common.permission.DeptDataScopeGuard;
import com.koujunxin.common.permission.KjxRoleKeys;
import com.koujunxin.member.api.MemberFailures;
import com.koujunxin.member.api.port.DeptAccessPort;

final class RuoYiDeptAccessAdapter implements DeptAccessPort {

    private final DeptDataScopeGuard guard;

    RuoYiDeptAccessAdapter(DeptDataScopeGuard guard) {
        this.guard = guard;
    }

    @Override
    public long requireActorId() {
        Long actorId = LoginHelper.getUserId();
        if (actorId == null) {
            throw MemberFailures.unauthorized();
        }
        return actorId;
    }

    @Override
    public long requireDeptId() {
        Long deptId = LoginHelper.getDeptId();
        if (deptId == null) {
            throw MemberFailures.forbidden();
        }
        return deptId;
    }

    @Override
    public void requireAccess(long resourceDeptId) {
        try {
            guard.requireDeptAccess(resourceDeptId);
        } catch (ServiceException ex) {
            if (Integer.valueOf(401).equals(ex.getCode())) {
                throw MemberFailures.unauthorized();
            }
            throw MemberFailures.forbidden();
        }
    }

    @Override
    public boolean ops() {
        return LoginHelper.isSuperAdmin()
                || guard.hasRole(KjxRoleKeys.OPERATOR);
    }

    @Override
    public boolean host() {
        return !ops() && guard.hasRole(KjxRoleKeys.HOST);
    }
}

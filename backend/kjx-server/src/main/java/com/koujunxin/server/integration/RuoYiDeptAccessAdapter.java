package com.koujunxin.server.integration;

import org.dromara.common.core.domain.dto.RoleDTO;
import org.dromara.common.core.domain.model.LoginUser;
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
        LoginUser user = LoginHelper.getLoginUser();
        Long actorId = user == null ? null : user.getUserId();
        if (actorId == null) {
            actorId = LoginHelper.getUserId();
        }
        if (actorId == null) {
            throw MemberFailures.unauthorized();
        }
        return actorId;
    }

    @Override
    public long requireDeptId() {
        LoginUser user = LoginHelper.getLoginUser();
        Long deptId = user == null ? null : user.getDeptId();
        if (deptId == null) {
            deptId = LoginHelper.getDeptId();
        }
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
        LoginUser user = LoginHelper.getLoginUser();
        Long userId = user == null ? null : user.getUserId();
        if (userId == null) {
            userId = LoginHelper.getUserId();
        }
        return LoginHelper.isSuperAdmin(userId) || hasRole(KjxRoleKeys.OPERATOR);
    }

    @Override
    public boolean host() {
        return !ops() && hasRole(KjxRoleKeys.HOST);
    }

    private boolean hasRole(String roleKey) {
        if (guard.hasRole(roleKey)) {
            return true;
        }
        LoginUser user = LoginHelper.getLoginUser();
        if (user == null || user.getRoles() == null) {
            return false;
        }
        for (RoleDTO role : user.getRoles()) {
            if (role != null && roleKey.equals(role.getRoleKey())) {
                return true;
            }
        }
        return false;
    }
}

package com.koujunxin.member.port;

import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.satoken.utils.StpKit;

import com.koujunxin.member.api.MemberFailures;
import com.koujunxin.member.api.port.DeptAccessPort;

/**
 * 默认后台权限：超管全放行，其余仅本部门。运营 ALL 数据范围需 kjx-server 覆盖本 bean。
 */
public final class LoginHelperDeptAccessPort implements DeptAccessPort {

    @Override
    public long requireActorId() {
        Long id = LoginHelper.getUserId();
        if (id == null) {
            throw MemberFailures.unauthorized();
        }
        return id;
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
        if (LoginHelper.isSuperAdmin()) {
            return;
        }
        if (ops()) {
            return;
        }
        if (resourceDeptId != requireDeptId()) {
            throw MemberFailures.forbidden();
        }
    }

    @Override
    public boolean ops() {
        try {
            var roles = LoginHelper.getLoginUser() == null ? java.util.Set.<String>of()
                    : LoginHelper.getLoginUser().getRolePermission();
            return roles != null && (roles.contains("OPERATOR") || roles.contains("superadmin"));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public boolean host() {
        try {
            var roles = LoginHelper.getLoginUser() == null ? java.util.Set.<String>of()
                    : LoginHelper.getLoginUser().getRolePermission();
            return roles != null && roles.contains("HOST") && !ops();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean adminLogin() {
        return StpKit.ADMIN.isLogin();
    }
}

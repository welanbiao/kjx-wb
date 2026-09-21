package com.koujunxin.server.support;

import org.dromara.common.core.domain.dto.RoleDTO;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.enums.UserType;

import java.util.List;
import java.util.Set;

public final class TestLoginUsers {

    private TestLoginUsers() {
    }

    public static LoginUser host(long userId, long deptId, String username) {
        LoginUser user = base(userId, deptId, username, UserType.SYS_USER.getUserType());
        RoleDTO role = new RoleDTO();
        role.setRoleId(10L);
        role.setRoleKey("HOST");
        role.setRoleName("主播");
        role.setDataScope("3");
        user.setRoles(List.of(role));
        user.setRolePermission(Set.of("HOST"));
        return user;
    }

    public static LoginUser operator(long userId, long deptId, String username) {
        LoginUser user = base(userId, deptId, username, UserType.SYS_USER.getUserType());
        RoleDTO role = new RoleDTO();
        role.setRoleId(11L);
        role.setRoleKey("OPERATOR");
        role.setRoleName("运营");
        role.setDataScope("1");
        user.setRoles(List.of(role));
        user.setRolePermission(Set.of("OPERATOR"));
        return user;
    }

    public static LoginUser app(long accountId, String username) {
        LoginUser user = base(accountId, 0L, username, UserType.APP_USER.getUserType());
        user.setRoles(List.of());
        user.setRolePermission(Set.of());
        return user;
    }

    private static LoginUser base(long userId, long deptId, String username, String userType) {
        LoginUser user = new LoginUser();
        user.setTenantId("000000");
        user.setUserId(userId);
        user.setDeptId(deptId);
        user.setDeptName("dept-" + deptId);
        user.setUsername(username);
        user.setNickname(username);
        user.setUserType(userType);
        user.setMenuPermission(Set.of());
        return user;
    }
}

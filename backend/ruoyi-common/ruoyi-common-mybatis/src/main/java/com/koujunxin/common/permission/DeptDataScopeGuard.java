package com.koujunxin.common.permission;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import org.dromara.common.core.domain.dto.RoleDTO;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.enums.DataScopeType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 部门数据权限强制约束：列表走 {@code @DataPermission} SQL，按 ID 查询/更新必须再走本 Guard，
 * 避免只过滤分页而放过 {@code selectById}/{@code updateById}。
 */
@Component
@Profile("!test")
public class DeptDataScopeGuard {

    public static final int FORBIDDEN = 403;

    private final SysDataScopeQuery dataScopeQuery;

    public DeptDataScopeGuard(SysDataScopeQuery dataScopeQuery) {
        this.dataScopeQuery = dataScopeQuery;
    }

    /**
     * 当前用户是否可访问指定 dept_id 的资源。超级管理员或含全部数据权限角色直接通过。
     */
    public void requireDeptAccess(Long resourceDeptId) {
        LoginUser user = LoginHelper.getLoginUser();
        if (user == null) {
            throw new ServiceException("认证失败，无法访问系统资源", 401);
        }
        if (LoginHelper.isSuperAdmin(user.getUserId())) {
            return;
        }
        if (resourceDeptId == null) {
            throw new ServiceException("无权访问其他部门数据", FORBIDDEN);
        }
        List<RoleDTO> roles = user.getRoles();
        if (CollUtil.isEmpty(roles)) {
            throw new ServiceException("无权访问其他部门数据", FORBIDDEN);
        }
        for (RoleDTO role : roles) {
            DataScopeType type = DataScopeType.findCode(role.getDataScope());
            if (type == null) {
                continue;
            }
            if (type == DataScopeType.ALL) {
                return;
            }
            if (type == DataScopeType.DEPT && Objects.equals(user.getDeptId(), resourceDeptId)) {
                return;
            }
            if (type == DataScopeType.DEPT_AND_CHILD
                    && dataScopeQuery.isDeptOrChild(user.getDeptId(), resourceDeptId)) {
                return;
            }
            if (type == DataScopeType.CUSTOM
                    && dataScopeQuery.isInCustomScope(role.getRoleId(), resourceDeptId)) {
                return;
            }
            if (type == DataScopeType.SELF) {
                throw new ServiceException("无权访问其他部门数据", FORBIDDEN);
            }
        }
        throw new ServiceException("无权访问其他部门数据", FORBIDDEN);
    }

    /**
     * 按 ID 加载后强制校验。entity 为 null 视为越权或不可见，不泄露是否存在。
     */
    public <T> T requireAccessible(T entity, Long deptId) {
        if (entity == null) {
            throw new ServiceException("记录不存在或无权访问", FORBIDDEN);
        }
        requireDeptAccess(deptId);
        return entity;
    }

    /**
     * 忽略 SQL 数据权限加载实体，再按 dept_id 做强制校验。供详情/修改/审核/导出复用。
     */
    public <T> T requireById(Supplier<T> loader, Function<T, Long> deptIdGetter) {
        T entity = loader.get();
        if (entity == null) {
            throw new ServiceException("记录不存在或无权访问", FORBIDDEN);
        }
        return requireAccessible(entity, deptIdGetter.apply(entity));
    }

    public Set<String> currentRoleKeys() {
        LoginUser user = LoginHelper.getLoginUser();
        if (user == null || CollUtil.isEmpty(user.getRolePermission())) {
            return Set.of();
        }
        return user.getRolePermission();
    }

    public boolean hasRole(String roleKey) {
        return currentRoleKeys().contains(roleKey);
    }

    public boolean isHostOnly() {
        Set<String> keys = currentRoleKeys();
        return keys.contains(KjxRoleKeys.HOST)
                && !keys.contains(KjxRoleKeys.OPERATOR)
                && !keys.contains(KjxRoleKeys.SUPERADMIN)
                && ObjectUtil.notEqual(LoginHelper.getUserId(), 1L);
    }
}

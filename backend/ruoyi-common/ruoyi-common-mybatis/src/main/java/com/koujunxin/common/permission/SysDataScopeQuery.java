package com.koujunxin.common.permission;

/**
 * 自定义/部门树数据范围查询。实现放在启动模块，避免 common 直接绑业务 Mapper。
 */
public interface SysDataScopeQuery {

    boolean isDeptOrChild(Long userDeptId, Long resourceDeptId);

    boolean isInCustomScope(Long roleId, Long resourceDeptId);
}

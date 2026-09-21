package com.koujunxin.member.api.port;

import java.util.Optional;

/**
 * 主播部门（sys_dept）命令/查询。kjx_host 不存 hostName；名称来自部门。
 * 本模块不得反向依赖 ruoyi 系统 Mapper。
 */
public interface HostDeptPort {

    long createDepartment(String hostName, Long parentDeptId, long adminUserId);

    void rename(long deptId, String hostName);

    Optional<String> findName(long deptId);
}

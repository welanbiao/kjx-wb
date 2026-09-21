package com.koujunxin.server.permission;

import com.koujunxin.common.permission.SysDataScopeQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RuoYi {@code @sdss} SpEL 与按 ID 部门树校验共用实现。
 */
@Service("sdss")
@Profile("!test")
public class SysDataScopeService implements SysDataScopeQuery {

    private final JdbcTemplate jdbcTemplate;

    public SysDataScopeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 供 {@code DataScopeType.CUSTOM} SpEL 使用，返回可被 SQL IN 的部门 ID 列表文本。
     */
    public String getRoleCustom(Long roleId) {
        if (roleId == null) {
            return "0";
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT dept_id FROM sys_role_dept WHERE role_id = ?",
                Long.class,
                roleId);
        if (ids.isEmpty()) {
            return "0";
        }
        return joinIds(ids);
    }

    /**
     * 供 {@code DataScopeType.DEPT_AND_CHILD} SpEL 使用。
     */
    public String getDeptAndChild(Long deptId) {
        if (deptId == null) {
            return "0";
        }
        List<Long> ids = jdbcTemplate.queryForList(
                """
                SELECT dept_id FROM sys_dept
                 WHERE del_flag = '0'
                   AND (dept_id = ? OR FIND_IN_SET(?, ancestors))
                """,
                Long.class,
                deptId,
                deptId);
        if (ids.isEmpty()) {
            return String.valueOf(deptId);
        }
        return joinIds(ids);
    }

    @Override
    public boolean isDeptOrChild(Long userDeptId, Long resourceDeptId) {
        if (userDeptId == null || resourceDeptId == null) {
            return false;
        }
        if (userDeptId.equals(resourceDeptId)) {
            return true;
        }
        Integer hit = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1) FROM sys_dept
                 WHERE del_flag = '0'
                   AND dept_id = ?
                   AND FIND_IN_SET(?, ancestors)
                """,
                Integer.class,
                resourceDeptId,
                userDeptId);
        return hit != null && hit > 0;
    }

    @Override
    public boolean isInCustomScope(Long roleId, Long resourceDeptId) {
        if (roleId == null || resourceDeptId == null) {
            return false;
        }
        Integer hit = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_role_dept WHERE role_id = ? AND dept_id = ?",
                Integer.class,
                roleId,
                resourceDeptId);
        return hit != null && hit > 0;
    }

    private static String joinIds(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }
}

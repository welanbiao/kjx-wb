package com.koujunxin.server.integration;

import java.util.Map;
import java.util.Optional;

import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.koujunxin.member.api.port.HostDeptPort;

final class JdbcHostDeptAdapter implements HostDeptPort {

    private final JdbcTemplate jdbc;
    private final IdentifierGenerator ids;
    private final long rootDeptId;

    JdbcHostDeptAdapter(JdbcTemplate jdbc, IdentifierGenerator ids, long rootDeptId) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.rootDeptId = rootDeptId;
    }

    @Override
    public long createDepartment(String hostName, Long parentDeptId, long adminUserId) {
        requireTransaction();
        String name = requireName(hostName);
        String tenantId = tenantId();
        long parentId = parentDeptId == null ? rootDeptId : parentDeptId;
        Map<String, Object> parent = jdbc.queryForMap(
                """
                SELECT tenant_id, ancestors
                  FROM sys_dept
                 WHERE dept_id = ? AND tenant_id = ? AND status = '0' AND del_flag = '0'
                """,
                parentId, tenantId);
        Object rawAncestors = parent.get("ancestors");
        String parentAncestors = rawAncestors == null ? "" : String.valueOf(rawAncestors);
        String ancestors = parentAncestors.isBlank() ? String.valueOf(parentId)
                : parentAncestors + "," + parentId;
        long actorId = actorId();
        long deptId = ids.nextId(null).longValue();
        jdbc.update(
                """
                INSERT INTO sys_dept
                  (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, leader,
                   status, del_flag, create_dept, create_by, create_time)
                VALUES (?, ?, ?, ?, ?, 99, ?, '0', '0', ?, ?, CURRENT_TIMESTAMP(3))
                """,
                deptId, tenantId, parentId, ancestors, name, adminUserId,
                LoginHelper.getDeptId(), actorId);

        int userUpdated = jdbc.update(
                """
                UPDATE sys_user
                   SET dept_id = ?, update_by = ?, update_time = CURRENT_TIMESTAMP(3)
                 WHERE user_id = ? AND tenant_id = ? AND status = '0' AND del_flag = '0'
                """,
                deptId, actorId, adminUserId, tenantId);
        if (userUpdated != 1) {
            throw new IllegalArgumentException("adminUserId 不存在或不可用");
        }
        Long hostRoleId = jdbc.query(
                """
                SELECT role_id
                  FROM sys_role
                 WHERE tenant_id = ? AND role_key = 'HOST' AND status = '0' AND del_flag = '0'
                 LIMIT 1
                """,
                rs -> rs.next() ? rs.getLong(1) : null,
                tenantId);
        if (hostRoleId == null) {
            throw new IllegalStateException("HOST role is not configured");
        }
        jdbc.update(
                """
                INSERT INTO sys_user_role (user_id, role_id)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
                """,
                adminUserId, hostRoleId);
        return deptId;
    }

    @Override
    public void rename(long deptId, String hostName) {
        requireTransaction();
        int updated = jdbc.update(
                """
                UPDATE sys_dept
                   SET dept_name = ?, update_by = ?, update_time = CURRENT_TIMESTAMP(3)
                 WHERE dept_id = ? AND tenant_id = ? AND del_flag = '0'
                """,
                requireName(hostName), actorId(), deptId, tenantId());
        if (updated != 1) {
            throw new IllegalArgumentException("主播部门不存在");
        }
    }

    @Override
    public Optional<String> findName(long deptId) {
        return jdbc.query(
                """
                SELECT dept_name
                  FROM sys_dept
                 WHERE dept_id = ? AND tenant_id = ? AND del_flag = '0'
                """,
                (rs, rowNum) -> rs.getString(1), deptId, tenantId()).stream().findFirst();
    }

    private static String requireName(String hostName) {
        if (hostName == null || hostName.isBlank() || hostName.trim().length() > 30) {
            throw new IllegalArgumentException("hostName 必填且不超过 30 个字符");
        }
        return hostName.trim();
    }

    private static long actorId() {
        Long actor = LoginHelper.getUserId();
        if (actor == null) {
            throw new IllegalStateException("admin login is required");
        }
        return actor;
    }

    private static String tenantId() {
        String tenant = LoginHelper.getTenantId();
        return tenant == null || tenant.isBlank() ? "000000" : tenant;
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("HostDeptPort writes require an active transaction");
        }
    }
}

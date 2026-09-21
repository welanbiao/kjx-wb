package com.koujunxin.server.flyway;

import java.util.List;

import com.koujunxin.account.api.port.BoundMemberQueryPort;
import com.koujunxin.member.api.port.AccountBindingCommandPort;
import com.koujunxin.member.api.port.DeptAccessPort;
import com.koujunxin.member.api.port.HostDeptPort;
import com.koujunxin.member.api.port.MemberAuditPort;
import com.koujunxin.server.support.InfraContainers;
import com.koujunxin.work.port.WorkAuditPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
class FlywayBaselineIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        InfraContainers.register(registry);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountBindingCommandPort accountBindingCommands;

    @Autowired
    private HostDeptPort hostDepartments;

    @Autowired
    private DeptAccessPort deptAccess;

    @Autowired
    private MemberAuditPort memberAudit;

    @Autowired
    private WorkAuditPort workAudit;

    @Autowired
    private BoundMemberQueryPort boundMembers;

    @Test
    void ruoyiBaselineAndKjxRolesMigrated() {
        Integer roles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role WHERE role_key IN ('HOST','OPERATOR','PUBLISHER','FINANCE')",
                Integer.class);
        assertEquals(4, roles);
        Integer charsetHits = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = DATABASE()
                   AND table_collation = 'utf8mb4_0900_ai_ci'
                   AND table_name IN ('sys_user','sys_role','test_demo')
                """,
                Integer.class);
        assertEquals(3, charsetHits);
    }

    @Test
    void baselineContainsNoKnownAccountsPasswordsOrClientSecrets() {
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_client", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_config WHERE config_key = 'sys.user.initPassword'",
                Integer.class));
    }

    @Test
    void moduleJarMigrationsAreDiscoveredFromStandardClasspathLocation() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1",
                String.class);
        assertTrue(versions.containsAll(List.of(
                "100", "101", "102",
                "200", "201", "202",
                "300", "301", "302", "303",
                "400", "401", "402")));
        assertTrue(versions.containsAll(List.of(
                "700", "701", "702", "703", "704", "705", "706", "707", "708")));
    }

    @Test
    void compositionRootOverridesAllProductionPlaceholders() {
        assertTrue(accountBindingCommands.getClass().getName().contains("server.integration"));
        assertTrue(hostDepartments.getClass().getName().contains("server.integration"));
        assertTrue(deptAccess.getClass().getName().contains("server.integration"));
        assertTrue(memberAudit.getClass().getName().contains("server.integration"));
        assertTrue(workAudit.getClass().getName().contains("server.integration"));
        assertTrue(boundMembers.getClass().getName().contains("JdbcBoundMemberQueryPort"));
    }

    @Test
    void accountBindingFailuresIncrementAtomicallyAndLockAtThree() {
        jdbcTemplate.update(
                """
                INSERT INTO kjx_account
                  (phone, phone_hash, password_hash, nickname, status, bind_status)
                VALUES ('cipher', 'integration-bind-hash', 'hash', 'integration', 1, 0)
                """);
        Long accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM kjx_account WHERE phone_hash = 'integration-bind-hash'",
                Long.class);
        assertNotNull(accountId);

        accountBindingCommands.recordFailure(accountId, 1, null);
        accountBindingCommands.recordFailure(accountId, 1, null);
        accountBindingCommands.recordFailure(accountId, 1, null);

        var state = accountBindingCommands.find(accountId).orElseThrow();
        assertEquals(3, state.bindFailCount());
        assertNotNull(state.bindLockedUntil());
    }

    @Test
    void emojiRoundTrip() {
        jdbcTemplate.update(
                "INSERT INTO test_demo (id, tenant_id, dept_id, user_id, order_num, test_key, value, version, create_dept, create_time, create_by, del_flag) "
                        + "VALUES (9001, '000000', 210, 101, 1, 'emoji', ?, 0, 210, NOW(), 101, 0)",
                "会员😀续费");
        String value = jdbcTemplate.queryForObject("SELECT value FROM test_demo WHERE id = 9001", String.class);
        assertTrue(value.contains("😀"));
    }
}

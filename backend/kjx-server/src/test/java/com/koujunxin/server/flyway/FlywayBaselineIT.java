package com.koujunxin.server.flyway;

import com.koujunxin.server.support.InfraContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void emojiRoundTrip() {
        jdbcTemplate.update(
                "INSERT INTO test_demo (id, tenant_id, dept_id, user_id, order_num, test_key, value, version, create_dept, create_time, create_by, del_flag) "
                        + "VALUES (9001, '000000', 210, 101, 1, 'emoji', ?, 0, 210, NOW(), 101, 0)",
                "会员😀续费");
        String value = jdbcTemplate.queryForObject("SELECT value FROM test_demo WHERE id = 9001", String.class);
        assertTrue(value.contains("😀"));
    }
}

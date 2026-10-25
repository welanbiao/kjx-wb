package com.koujunxin.server.permission;

import com.koujunxin.server.security.DualDomainLoginSupport;
import com.koujunxin.server.support.InfraContainers;
import com.koujunxin.server.support.TestLoginUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate 2：会员、绑定、权益、解锁记录按部门隔离。探测表用例见 {@link DeptDataScopeIT}。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
class BusinessDeptIsolationIT {

    private static final long DEPT_A = 910_210L;
    private static final long DEPT_B = 910_211L;
    private static final long MEMBER_A = 9_201L;
    private static final long MEMBER_B = 9_202L;
    private static final long ACCOUNT_A = 8_001L;
    private static final long ACCOUNT_B = 8_002L;
    private static final long WORK_A = 9_401L;
    private static final long WORK_B = 9_402L;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        InfraContainers.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String hostAToken;
    private String hostBToken;
    private String operatorToken;
    private String appAToken;
    private String appBToken;

    @BeforeEach
    void seedAndLogin() {
        jdbcTemplate.update("DELETE FROM kjx_unlock_record WHERE id IN (9601, 9602)");
        jdbcTemplate.update("DELETE FROM kjx_member_entitlement WHERE id IN (9501, 9502)");
        jdbcTemplate.update("DELETE FROM kjx_member_binding WHERE id IN (9301, 9302)");
        jdbcTemplate.update("DELETE FROM kjx_member WHERE id IN (?, ?)", MEMBER_A, MEMBER_B);
        jdbcTemplate.update("DELETE FROM kjx_work WHERE id IN (?, ?)", WORK_A, WORK_B);
        jdbcTemplate.update("DELETE FROM kjx_host WHERE dept_id IN (?, ?)", DEPT_A, DEPT_B);

        jdbcTemplate.update(
                "INSERT INTO kjx_host (dept_id, host_code) VALUES (?, 'gateb-a'), (?, 'gateb-b')",
                DEPT_A, DEPT_B);
        jdbcTemplate.update(
                """
                INSERT INTO kjx_member
                  (id, member_code, douyin_id, dept_id, subscribed_months, subscribed_days, expire_at,
                   status, bind_state, bound_account_id, deleted)
                VALUES
                  (?, 'gateb-a-00000001', 'gateb-dy-a', ?, 1, 30, '2030-01-01 00:00:00.000', 'VALID', 1, ?, b'0'),
                  (?, 'gateb-b-00000002', 'gateb-dy-b', ?, 1, 30, '2030-01-01 00:00:00.000', 'VALID', 1, ?, b'0')
                """,
                MEMBER_A, DEPT_A, ACCOUNT_A, MEMBER_B, DEPT_B, ACCOUNT_B);
        jdbcTemplate.update(
                """
                INSERT INTO kjx_member_binding
                  (id, dept_id, account_id, member_id, douyin_id_input, member_code_input, claim_months, status)
                VALUES
                  (9301, ?, ?, ?, 'gateb-dy-a', 'gateb-a-00000001', 1, 'MATCHED'),
                  (9302, ?, ?, ?, 'gateb-dy-b', 'gateb-b-00000002', 1, 'MATCHED')
                """,
                DEPT_A, ACCOUNT_A, MEMBER_A, DEPT_B, ACCOUNT_B, MEMBER_B);
        jdbcTemplate.update(
                """
                INSERT INTO kjx_work (id, work_code, name, work_type, status, deleted)
                VALUES (?, 'gate-b-work-a', 'Gate B A', 'layout', 'PUBLISHED', b'0'),
                       (?, 'gate-b-work-b', 'Gate B B', 'layout', 'PUBLISHED', b'0')
                """,
                WORK_A, WORK_B);
        jdbcTemplate.update(
                """
                INSERT INTO kjx_member_entitlement
                  (id, dept_id, member_id, account_id, work_id, source)
                VALUES (9501, ?, ?, ?, ?, 'user'),
                       (9502, ?, ?, ?, ?, 'user')
                """,
                DEPT_A, MEMBER_A, ACCOUNT_A, WORK_A, DEPT_B, MEMBER_B, ACCOUNT_B, WORK_B);
        jdbcTemplate.update(
                """
                INSERT INTO kjx_unlock_record
                  (id, dept_id, member_id, member_code, work_id, result, source)
                VALUES (9601, ?, ?, 'gateb-a-00000001', ?, 'SUCCESS', 'user'),
                       (9602, ?, ?, 'gateb-b-00000002', ?, 'SUCCESS', 'user')
                """,
                DEPT_A, MEMBER_A, WORK_A, DEPT_B, MEMBER_B, WORK_B);

        hostAToken = DualDomainLoginSupport.loginAdmin(TestLoginUsers.host(101L, DEPT_A, "gateHostA"));
        hostBToken = DualDomainLoginSupport.loginAdmin(TestLoginUsers.host(102L, DEPT_B, "gateHostB"));
        operatorToken = DualDomainLoginSupport.loginAdmin(TestLoginUsers.operator(103L, 201L, "gateOperator"));
        appAToken = DualDomainLoginSupport.loginApp(TestLoginUsers.app(ACCOUNT_A, "gateAppA"));
        appBToken = DualDomainLoginSupport.loginApp(TestLoginUsers.app(ACCOUNT_B, "gateAppB"));
    }

    @Test
    void hostAMemberPageHidesHostBAndCannotReadById() throws Exception {
        mockMvc.perform(get("/admin-api/kjx/self/member/page")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.id==" + MEMBER_A + ")]").exists())
                .andExpect(jsonPath("$.data.list[?(@.id==" + MEMBER_B + ")]").doesNotExist());

        mockMvc.perform(get("/admin-api/kjx/self/member/" + MEMBER_B + "/code")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/admin-api/kjx/self/member/" + MEMBER_A + "/code")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCode").value("gateb-a-00000001"));
    }

    @Test
    void hostABindingPageHidesHostB() throws Exception {
        mockMvc.perform(get("/admin-api/kjx/self/binding/page")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.id==9301)]").exists())
                .andExpect(jsonPath("$.data.list[?(@.id==9302)]").doesNotExist());

        mockMvc.perform(get("/admin-api/kjx/binding/page")
                        .param("deptId", String.valueOf(DEPT_B))
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void hostAUnlockPageHidesHostBAndRejectsForeignDept() throws Exception {
        mockMvc.perform(get("/admin-api/kjx/unlock-record/page")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.id==9601)]").exists())
                .andExpect(jsonPath("$.data.list[?(@.id==9602)]").doesNotExist());

        mockMvc.perform(get("/admin-api/kjx/unlock-record/page")
                        .param("deptId", String.valueOf(DEPT_B))
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void appEntitlementsStayOnTheBoundAccount() throws Exception {
        mockMvc.perform(get("/app/entitlements")
                        .header("Authorization", "Bearer " + appAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.workCode=='gate-b-work-a')]").exists())
                .andExpect(jsonPath("$.data.list[?(@.workCode=='gate-b-work-b')]").doesNotExist());

        mockMvc.perform(get("/app/entitlements")
                        .header("Authorization", "Bearer " + appBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.workCode=='gate-b-work-b')]").exists())
                .andExpect(jsonPath("$.data.list[?(@.workCode=='gate-b-work-a')]").doesNotExist());
    }

    @Test
    void hostCannotAdjustForeignEntitlementAndOperatorCanReadBothMembers() throws Exception {
        mockMvc.perform(post("/admin-api/kjx/member/" + MEMBER_B + "/entitlement")
                        .header("Authorization", "Bearer " + hostAToken)
                        .header("X-Idempotency-Key", "gate-b-adjust")
                        .contentType("application/json")
                        .content("{\"action\":\"ADD\",\"workCode\":\"gate-b-work-b\",\"reason\":\"no\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin-api/kjx/member/" + MEMBER_B)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deptId").value(DEPT_B));

        mockMvc.perform(get("/admin-api/kjx/member/page")
                        .param("deptId", String.valueOf(DEPT_A))
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.id==" + MEMBER_A + ")]").exists())
                .andExpect(jsonPath("$.data.list[?(@.id==" + MEMBER_B + ")]").doesNotExist());

        mockMvc.perform(get("/admin-api/kjx/self/member/page")
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.id==" + MEMBER_B + ")]").exists())
                .andExpect(jsonPath("$.data.list[?(@.id==" + MEMBER_A + ")]").doesNotExist());
    }
}

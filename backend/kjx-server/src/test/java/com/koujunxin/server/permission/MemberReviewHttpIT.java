package com.koujunxin.server.permission;

import com.koujunxin.server.security.DualDomainLoginSupport;
import com.koujunxin.server.support.InfraContainers;
import com.koujunxin.server.support.TestLoginUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WP-11：主播录入、月/天独立、三要素绑定、运营审核和 Outbox 同一次 HTTP 闭环。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
class MemberReviewHttpIT {

    private static final long DEPT = 910_310L;
    private static final String DOUYIN = "mrevdy";
    private static final String EXPIRE = "2027-08-09T23:59:59.999+08:00";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        InfraContainers.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String hostToken;
    private String operatorToken;
    private String appToken;
    private long accountId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM kjx_member_binding WHERE dept_id = ?", DEPT);
        jdbcTemplate.update("DELETE FROM kjx_member WHERE dept_id = ?", DEPT);
        jdbcTemplate.update("DELETE FROM kjx_host WHERE dept_id = ?", DEPT);
        jdbcTemplate.update("DELETE FROM kjx_account WHERE nickname = 'mrev'");
        jdbcTemplate.update("INSERT INTO kjx_host (dept_id, host_code) VALUES (?, 'MREV')", DEPT);
        jdbcTemplate.update(
                """
                INSERT INTO kjx_account (phone, phone_hash, password_hash, nickname, status, bind_status)
                VALUES ('mrev-cipher', 'mrev-phone-hash', 'x', 'mrev', 1, 0)
                """);
        accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM kjx_account WHERE nickname = 'mrev'", Long.class);
        hostToken = DualDomainLoginSupport.loginAdmin(TestLoginUsers.host(301L, DEPT, "mrevHost"));
        operatorToken = DualDomainLoginSupport.loginAdmin(TestLoginUsers.operator(302L, 201L, "mrevOps"));
        appToken = DualDomainLoginSupport.loginApp(TestLoginUsers.app(accountId, "mrevApp"));
    }

    @Test
    void hostEntryBindingReviewKeepsMonthsIndependentOfQuota() throws Exception {
        mockMvc.perform(post("/admin-api/kjx/self/member")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("X-Idempotency-Key", "mrev-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"douyinId":"%s","subscribedMonths":15,"subscribedDays":435,"expireAt":"%s","remark":""}
                                """.formatted(DOUYIN, EXPIRE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.availableCount").value(14));

        Long memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM kjx_member WHERE dept_id = ? AND douyin_id = ?", Long.class, DEPT, DOUYIN);
        assertEquals(15, jdbcTemplate.queryForObject(
                "SELECT subscribed_months FROM kjx_member WHERE id = ?", Integer.class, memberId));
        assertEquals(435, jdbcTemplate.queryForObject(
                "SELECT subscribed_days FROM kjx_member WHERE id = ?", Integer.class, memberId));
        String memberCode = jdbcTemplate.queryForObject(
                "SELECT member_code FROM kjx_member WHERE id = ?", String.class, memberId);

        mockMvc.perform(put("/admin-api/kjx/self/member/" + memberId)
                        .header("Authorization", "Bearer " + hostToken)
                        .header("X-Idempotency-Key", "mrev-months")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subscribedMonths\":20,\"reason\":\"只改月数\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribedMonths").value(20))
                .andExpect(jsonPath("$.data.availableCount").value(14))
                .andExpect(jsonPath("$.data.subscribedDays").value(435));

        mockMvc.perform(post("/app/member/binding")
                        .header("Authorization", "Bearer " + appToken)
                        .header("X-Idempotency-Key", "mrev-bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hostCode":"MREV","douyinId":"%s","memberCode":"%s","claimMonths":20}
                                """.formatted(DOUYIN, memberCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Long bindingId = jdbcTemplate.queryForObject(
                "SELECT id FROM kjx_member_binding WHERE account_id = ?", Long.class, accountId);
        assertEquals("PENDING_REVIEW", jdbcTemplate.queryForObject(
                "SELECT status FROM kjx_member_binding WHERE id = ?", String.class, bindingId));

        mockMvc.perform(post("/admin-api/kjx/binding/" + bindingId + "/approve")
                        .header("Authorization", "Bearer " + operatorToken)
                        .header("X-Idempotency-Key", "mrev-approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertEquals("MATCHED", jdbcTemplate.queryForObject(
                "SELECT status FROM kjx_member_binding WHERE id = ?", String.class, bindingId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT bind_state FROM kjx_member WHERE id = ?", Integer.class, memberId));
        assertEquals(20, jdbcTemplate.queryForObject(
                "SELECT subscribed_months FROM kjx_member WHERE id = ?", Integer.class, memberId));
        assertEquals(14, jdbcTemplate.queryForObject(
                "SELECT available_count FROM kjx_member WHERE id = ?", Integer.class, memberId));
        assertEquals(1, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM kjx_outbox_event
                 WHERE event_type = 'binding.approved' AND aggregate_id = ?
                """,
                Integer.class, String.valueOf(bindingId)));
    }
}

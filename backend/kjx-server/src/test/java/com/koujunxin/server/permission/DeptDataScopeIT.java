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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
class DeptDataScopeIT {

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

    @BeforeEach
    void seedAndLogin() {
        jdbcTemplate.update("DELETE FROM test_demo WHERE id IN (9101, 9102)");
        jdbcTemplate.update(
                "INSERT INTO test_demo (id, tenant_id, dept_id, user_id, order_num, test_key, value, version, create_dept, create_time, create_by, del_flag) "
                        + "VALUES (9101, '000000', 210, 101, 1, 'host-a', 'A', 0, 210, NOW(), 101, 0),"
                        + "(9102, '000000', 211, 102, 1, 'host-b', 'B', 0, 211, NOW(), 102, 0)");
        hostAToken = DualDomainLoginSupport.loginAdmin(TestLoginUsers.host(101L, 210L, "hostA"));
        hostBToken = DualDomainLoginSupport.loginAdmin(TestLoginUsers.host(102L, 211L, "hostB"));
        operatorToken = DualDomainLoginSupport.loginAdmin(TestLoginUsers.operator(103L, 201L, "operator"));
    }

    @Test
    void hostAListDoesNotContainHostB() throws Exception {
        mockMvc.perform(get("/admin-api/kjx/infra/probe")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==9101)]").exists())
                .andExpect(jsonPath("$.data[?(@.id==9102)]").doesNotExist());
    }

    @Test
    void hostACannotGetHostBById() throws Exception {
        mockMvc.perform(get("/admin-api/kjx/infra/probe/9102")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void hostACannotUpdateHostBById() throws Exception {
        mockMvc.perform(put("/admin-api/kjx/infra/probe/9102")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"hacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanReadBoth() throws Exception {
        mockMvc.perform(get("/admin-api/kjx/infra/probe")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==9101)]").exists())
                .andExpect(jsonPath("$.data[?(@.id==9102)]").exists());
        mockMvc.perform(get("/admin-api/kjx/infra/probe/9102")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deptId").value(211));
    }

    @Test
    void hostBSeesOwnRow() throws Exception {
        mockMvc.perform(get("/admin-api/kjx/infra/probe")
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==9102)]").exists())
                .andExpect(jsonPath("$.data[?(@.id==9101)]").doesNotExist());
    }
}

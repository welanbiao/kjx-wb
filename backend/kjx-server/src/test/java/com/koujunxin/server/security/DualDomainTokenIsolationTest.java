package com.koujunxin.server.security;

import com.koujunxin.server.support.TestLoginUsers;
import org.dromara.common.satoken.utils.StpKit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DualDomainTokenIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    private String appToken;
    private String adminToken;

    @BeforeEach
    void login() {
        appToken = DualDomainLoginSupport.loginApp(TestLoginUsers.app(9001L, "member"));
        adminToken = DualDomainLoginSupport.loginAdmin(TestLoginUsers.operator(1L, 201L, "operator"));
    }

    @Test
    void appTokenCallingAdminIs401() throws Exception {
        mockMvc.perform(get("/admin-api/kjx/infra/ping")
                        .header("Authorization", "Bearer " + appToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void adminTokenCallingAppIs401() throws Exception {
        mockMvc.perform(get("/app/infra/ping")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void missingTokenIs401() throws Exception {
        mockMvc.perform(get("/app/infra/ping"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin-api/kjx/infra/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void matchingDomainSucceeds() throws Exception {
        mockMvc.perform(get("/app/infra/ping")
                        .header("Authorization", "Bearer " + appToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.domain").value(StpKit.APP_TYPE));
        mockMvc.perform(get("/admin-api/kjx/infra/ping")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.domain").value(StpKit.ADMIN_TYPE));
    }
}

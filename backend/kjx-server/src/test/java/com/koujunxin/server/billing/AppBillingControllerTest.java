package com.koujunxin.server.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.koujunxin.billing.api.BillingActor;
import com.koujunxin.billing.api.CreditCommand;
import com.koujunxin.billing.api.PlatformBilling;
import com.koujunxin.billing.jdbc.JdbcBillingService;
import com.koujunxin.channel.api.ChannelCredentialStore;
import com.koujunxin.channel.api.SecretRef;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.server.billing.AppBillingController.BoundMember;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppBillingControllerTest {

    private static final String PLAIN_KEY = "sk-live-secret";

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private ObjectProvider<PlatformBilling> billingProvider;
    @Mock
    private ObjectProvider<ChannelCredentialStore> credentialProvider;
    @Mock
    private ObjectProvider<RequestContextPort> contextProvider;
    @Mock
    private JdbcBillingService ledger;
    @Mock
    private ChannelCredentialStore store;

    private final RuntimeAccountPort accounts = () -> 7L;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(billingProvider.getIfAvailable()).thenReturn(ledger);
        when(credentialProvider.getIfAvailable()).thenReturn(store);
        when(contextProvider.getIfAvailable()).thenReturn(null);
        mockMvc = MockMvcBuilders.standaloneSetup(new AppBillingController(
                        jdbc, billingProvider, credentialProvider, accounts, contextProvider, "test-hmac-secret"))
                .setControllerAdvice(new BillingExceptionAdvice())
                .build();
        doReturn(List.of(new BoundMember(3L, 11L)))
                .when(jdbc)
                .query(eq(AppBillingController.BOUND_MEMBER_SQL), ArgumentMatchers.<RowMapper<?>>any(), eq(7L));
    }

    @Test
    void otherMemberWalletIsForbiddenAndDoesNotReadBalance() throws Exception {
        mockMvc.perform(get("/app/billing/wallet")
                        .param("memberId", "99")
                        .header("X-Request-Id", "req-wallet-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.requestId").value("req-wallet-1"));
        verify(ledger, never()).available(anyLong(), any(BillingActor.class));
    }

    @Test
    void failedPaymentSignatureDoesNotCredit() throws Exception {
        mockMvc.perform(post("/app/billing/payments/notify")
                        .header("X-Idempotency-Key", "pay-1")
                        .header("X-Request-Id", "req-pay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"ALIPAY","outTradeNo":"trade-1","amount":"10.000000",\
                                "memberId":3,"idempotencyKey":"pay-1","signature":"00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(ledger, never()).credit(any(CreditCommand.class));
    }

    @Test
    void plaintextSecretRefIsRejectedWithoutEcho() throws Exception {
        mockMvc.perform(post("/app/billing/byok-credentials")
                        .header("X-Request-Id", "req-byok-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"OPENAI","secretRef":"%s","endpointHost":"api.openai.com"}
                                """.formatted(PLAIN_KEY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(content().string(Matchers.not(Matchers.containsString(PLAIN_KEY))));
        verify(store, never()).save(anyLong(), anyLong(), anyString(), any(SecretRef.class), anyString());
    }
}

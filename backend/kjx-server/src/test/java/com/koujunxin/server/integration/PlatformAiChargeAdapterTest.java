package com.koujunxin.server.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.koujunxin.ai.api.AiChannelMode;
import com.koujunxin.ai.api.TokenUsage;
import com.koujunxin.ai.api.port.AiChargePort.ChargeContext;
import com.koujunxin.ai.api.port.AiChargePort.Hold;
import com.koujunxin.billing.api.FreezeCommand;
import com.koujunxin.billing.api.PlatformBilling;
import com.koujunxin.billing.api.ReleaseCommand;
import com.koujunxin.billing.api.SettleCommand;
import com.koujunxin.billing.ledger.Money;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.server.integration.PlatformAiChargeAdapter.BoundMember;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformAiChargeAdapterTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private ObjectProvider<PlatformBilling> billingProvider;
    @Mock
    private ObjectProvider<RequestContextPort> contextProvider;

    private RecordingBilling billing;
    private PlatformAiChargeAdapter adapter;

    @BeforeEach
    void setUp() {
        billing = new RecordingBilling();
        when(billingProvider.getIfAvailable()).thenReturn(billing);
        when(contextProvider.getIfAvailable()).thenReturn(context("trace-ai", "req-ai"));
        adapter = new PlatformAiChargeAdapter(
                billingProvider, jdbc, contextProvider, Money.of("1.000000"), 1L);
    }

    @Test
    void byokMustNotFreeze() {
        ChargeContext byok = new ChargeContext(7L, "demo", "s1", "turn-1", "key-1", AiChannelMode.BYOK);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> adapter.freezePlatform(byok));
        assertTrue(ex.getMessage().contains("BYOK must not freeze"));
        assertTrue(billing.freezes.isEmpty());
        verify(jdbc, never()).query(anyString(), ArgumentMatchers.<RowMapper<?>>any(), any());
    }

    @Test
    void platformFreezeGoesToBillingAndSettleDoesNotExceedHold() {
        doReturn(List.of(new BoundMember(3L, 11L)))
                .when(jdbc).query(eq(PlatformAiChargeAdapter.MEMBER_SQL), ArgumentMatchers.<RowMapper<?>>any(), eq(7L));
        doReturn(List.of(9L))
                .when(jdbc).query(eq(PlatformAiChargeAdapter.WORK_SQL), ArgumentMatchers.<RowMapper<?>>any(), eq("demo"));

        ChargeContext context = new ChargeContext(7L, "demo", "s1", "turn-1", "idem-1", AiChannelMode.PLATFORM);
        Hold hold = adapter.freezePlatform(context);

        assertEquals(1, billing.freezes.size());
        FreezeCommand freeze = billing.freezes.get(0);
        assertEquals(3L, freeze.memberId());
        assertEquals(Money.of("1.000000"), freeze.amount());
        assertEquals("ai.platform", freeze.reason());
        assertEquals(9L, freeze.workId());
        assertEquals(11L, freeze.actor().deptId());
        assertEquals("idem-1", freeze.idempotencyKey());
        assertTrue(hold.active());

        adapter.settle(hold, new TokenUsage(2_000_000, 1, 0));
        assertEquals(1, billing.settles.size());
        SettleCommand settle = billing.settles.get(0);
        assertTrue(settle.actual().compareTo(Money.of("1.000000")) <= 0);
        assertEquals(Money.of("1.000000"), settle.actual());
        assertEquals("idem-1:settle", settle.idempotencyKey());
        assertTrue(settle.idempotencyKey().length() <= 64);
        assertEquals(2_000_000, settle.inputTokens());
        assertEquals(1, settle.outputTokens());
    }

    @Test
    void missingBoundMemberFails() {
        doReturn(List.of())
                .when(jdbc).query(eq(PlatformAiChargeAdapter.MEMBER_SQL), ArgumentMatchers.<RowMapper<?>>any(), eq(7L));
        ChargeContext context = new ChargeContext(7L, "demo", "s1", "turn-1", "idem-1", AiChannelMode.PLATFORM);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> adapter.freezePlatform(context));
        assertTrue(ex.getMessage().contains("no bound member"));
        assertTrue(billing.freezes.isEmpty());
    }

    @Test
    void inactiveHoldDoesNotSettleOrRelease() {
        adapter.settle(Hold.none(), new TokenUsage(10, 10, 0));
        adapter.release(Hold.none(), "cancel");
        assertTrue(billing.settles.isEmpty());
        assertTrue(billing.releases.isEmpty());
    }

    private static RequestContextPort context(String traceId, String requestId) {
        return new RequestContextPort() {
            @Override
            public RequestContextSnapshot current() {
                return RequestContextSnapshot.of(requestId, traceId);
            }

            @Override
            public void bind(RequestContextSnapshot snapshot) {
            }

            @Override
            public void clear() {
            }
        };
    }

    private static final class RecordingBilling implements PlatformBilling {
        private final List<FreezeCommand> freezes = new ArrayList<>();
        private final List<SettleCommand> settles = new ArrayList<>();
        private final List<ReleaseCommand> releases = new ArrayList<>();
        private long nextId = 41L;

        @Override
        public long freeze(FreezeCommand command) {
            freezes.add(command);
            return nextId++;
        }

        @Override
        public long settle(SettleCommand command) {
            if (command.actual().compareTo(Money.of("1.000000")) > 0) {
                throw new AssertionError("settle exceeded freeze");
            }
            settles.add(command);
            return 100L;
        }

        @Override
        public void release(ReleaseCommand command) {
            releases.add(command);
        }
    }
}

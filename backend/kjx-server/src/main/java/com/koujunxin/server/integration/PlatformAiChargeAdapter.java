package com.koujunxin.server.integration;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.ai.api.AiChannelMode;
import com.koujunxin.ai.api.TokenUsage;
import com.koujunxin.ai.api.port.AiChargePort;
import com.koujunxin.billing.api.BillingActor;
import com.koujunxin.billing.api.FreezeCommand;
import com.koujunxin.billing.api.PlatformBilling;
import com.koujunxin.billing.api.ReleaseCommand;
import com.koujunxin.billing.api.SettleCommand;
import com.koujunxin.billing.ledger.CanonicalHash;
import com.koujunxin.billing.ledger.Money;
import com.koujunxin.log.api.port.RequestContextPort;

/**
 * PLATFORM 通道冻结/结算接到 {@link PlatformBilling}。BYOK 不得调用 freeze。
 */
final class PlatformAiChargeAdapter implements AiChargePort {

    static final String REASON = "ai.platform";
    static final String MEMBER_SQL = """
            SELECT id, dept_id FROM kjx_member
             WHERE bound_account_id = ? AND bind_state = 1 AND deleted = 0
             LIMIT 1
            """;
    static final String WORK_SQL = """
            SELECT id FROM kjx_work
             WHERE work_code = ? AND deleted = b'0'
             LIMIT 1
            """;

    private final ObjectProvider<PlatformBilling> billing;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<RequestContextPort> contexts;
    private final Money holdAmount;
    private final long microsPerToken;
    private final ConcurrentMap<Long, FreezeSession> sessions = new ConcurrentHashMap<>();

    PlatformAiChargeAdapter(
            ObjectProvider<PlatformBilling> billing,
            JdbcTemplate jdbc,
            ObjectProvider<RequestContextPort> contexts,
            Money holdAmount,
            long microsPerToken) {
        if (holdAmount == null || holdAmount.micros() <= 0) {
            throw new IllegalArgumentException("kjx.billing.ai-hold-amount must be > 0");
        }
        if (microsPerToken < 0) {
            throw new IllegalArgumentException("kjx.billing.platform-micros-per-token must be >= 0");
        }
        this.billing = billing;
        this.jdbc = jdbc;
        this.contexts = contexts;
        this.holdAmount = holdAmount;
        this.microsPerToken = microsPerToken;
    }

    @Override
    public Hold freezePlatform(ChargeContext context) {
        if (context != null && context.channel() == AiChannelMode.BYOK) {
            throw new IllegalStateException("BYOK must not freeze platform balance");
        }
        if (context == null) {
            throw new IllegalStateException("charge context is required");
        }
        BoundMember member = lookupBoundMember(context.accountId());
        Long workId = lookupWorkId(context.workCode());
        RequestContextPort.RequestContextSnapshot snapshot = snapshot();
        BillingActor actor = new BillingActor(
                member.deptId(),
                false,
                snapshot == null ? null : snapshot.traceId(),
                snapshot == null ? null : snapshot.requestId());
        String freezeKey = freezeKey(context);
        String payloadHash = payloadHash(context, holdAmount);
        long holdId = requireBilling().freeze(new FreezeCommand(
                member.id(),
                holdAmount,
                freezeKey,
                payloadHash,
                REASON,
                workId,
                actor));
        sessions.put(holdId, new FreezeSession(holdAmount, actor, freezeKey, context));
        return new Hold(holdId, true);
    }

    @Override
    public void settle(Hold hold, TokenUsage usage) {
        if (hold == null || !hold.active()) {
            return;
        }
        FreezeSession session = sessions.get(hold.id());
        if (session == null) {
            throw new IllegalStateException("active hold has no freeze session");
        }
        TokenUsage tokens = usage == null ? TokenUsage.zero() : usage;
        Money actual = capSettle(session.amount(), tokens);
        String settleKey = settleKey(session.freezeKey());
        String payloadHash = payloadHash(session.context(), actual);
        requireBilling().settle(new SettleCommand(
                hold.id(),
                actual,
                settleKey,
                payloadHash,
                tokens.promptTokens(),
                tokens.completionTokens(),
                null,
                session.actor()));
        sessions.remove(hold.id());
    }

    @Override
    public void release(Hold hold, String reason) {
        if (hold == null || !hold.active()) {
            return;
        }
        FreezeSession session = sessions.remove(hold.id());
        if (session == null) {
            return;
        }
        String releaseReason = reason == null || reason.isBlank() ? "release" : reason.trim();
        requireBilling().release(new ReleaseCommand(hold.id(), releaseReason, session.actor()));
    }

    private BoundMember lookupBoundMember(long accountId) {
        List<BoundMember> rows = jdbc.query(
                MEMBER_SQL,
                (rs, rowNum) -> new BoundMember(rs.getLong("id"), rs.getLong("dept_id")),
                accountId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("no bound member for account, cannot freeze platform balance");
        }
        BoundMember member = rows.get(0);
        if (member.deptId() <= 0) {
            throw new IllegalStateException("bound member deptId is invalid");
        }
        return member;
    }

    private Long lookupWorkId(String workCode) {
        if (workCode == null || workCode.isBlank()) {
            return null;
        }
        List<Long> ids = jdbc.query(WORK_SQL, (rs, rowNum) -> rs.getLong("id"), workCode);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private PlatformBilling requireBilling() {
        PlatformBilling port = billing == null ? null : billing.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("PlatformBilling is required for PLATFORM freeze");
        }
        return port;
    }

    private RequestContextPort.RequestContextSnapshot snapshot() {
        RequestContextPort port = contexts == null ? null : contexts.getIfAvailable();
        if (port == null) {
            return null;
        }
        try {
            return port.current();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Money capSettle(Money frozen, TokenUsage usage) {
        long tokenMicros;
        try {
            tokenMicros = Math.multiplyExact((long) usage.totalTokens(), microsPerToken);
        } catch (ArithmeticException ex) {
            tokenMicros = frozen.micros();
        }
        if (tokenMicros >= frozen.micros()) {
            return frozen;
        }
        return new Money(tokenMicros);
    }

    static String freezeKey(ChargeContext context) {
        String base = firstNonBlank(context.idempotencyKey(), context.turnId());
        if (base == null) {
            throw new IllegalStateException("idempotencyKey or turnId is required");
        }
        return truncate(base, 64);
    }

    static String settleKey(String freezeKey) {
        String suffix = ":settle";
        String base = freezeKey == null ? "" : freezeKey;
        if (base.endsWith(suffix)) {
            return truncate(base, 64);
        }
        return truncate(base, 64 - suffix.length()) + suffix;
    }

    static String payloadHash(ChargeContext context, Money amount) {
        String workCode = context.workCode() == null ? "" : context.workCode();
        String sessionId = context.sessionId() == null ? "" : context.sessionId();
        String turnId = context.turnId() == null ? "" : context.turnId();
        String amountText = amount.decimal().toPlainString();
        return CanonicalHash.sha256(
                context.accountId() + "|" + workCode + "|" + sessionId + "|" + turnId + "|" + amountText);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    record BoundMember(long id, long deptId) {
    }

    private record FreezeSession(Money amount, BillingActor actor, String freezeKey, ChargeContext context) {
    }
}

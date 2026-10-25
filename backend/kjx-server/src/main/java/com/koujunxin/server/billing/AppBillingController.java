package com.koujunxin.server.billing;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.account.web.AccountWebSupport;
import com.koujunxin.account.web.ApiBody;
import com.koujunxin.billing.api.BillingActor;
import com.koujunxin.billing.api.BillingException;
import com.koujunxin.billing.api.CreditCommand;
import com.koujunxin.billing.api.PlatformBilling;
import com.koujunxin.billing.jdbc.JdbcBillingService;
import com.koujunxin.billing.ledger.CanonicalHash;
import com.koujunxin.billing.ledger.Money;
import com.koujunxin.billing.payment.HmacPaymentVerifier;
import com.koujunxin.channel.api.ChannelCredentialStore;
import com.koujunxin.channel.api.SecretRef;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.runtime.api.RuntimeAccountPort;

@RestController
@ConditionalOnBean(JdbcTemplate.class)
public class AppBillingController {

    static final String BOUND_MEMBER_SQL = """
            SELECT id, dept_id FROM kjx_member
             WHERE bound_account_id = ? AND bind_state = 1 AND deleted = 0
             LIMIT 1
            """;
    static final String MEMBER_DEPT_SQL = """
            SELECT id, dept_id FROM kjx_member
             WHERE id = ? AND deleted = 0
             LIMIT 1
            """;

    private static final Logger log = LoggerFactory.getLogger(AppBillingController.class);

    private final JdbcTemplate jdbc;
    private final ObjectProvider<PlatformBilling> billing;
    private final ObjectProvider<ChannelCredentialStore> credentials;
    private final RuntimeAccountPort accounts;
    private final ObjectProvider<RequestContextPort> contexts;
    private final String paymentHmacSecret;

    public AppBillingController(
            JdbcTemplate jdbc,
            ObjectProvider<PlatformBilling> billing,
            ObjectProvider<ChannelCredentialStore> credentials,
            RuntimeAccountPort accounts,
            ObjectProvider<RequestContextPort> contexts,
            @Value("${kjx.billing.payment-hmac-secret:}") String paymentHmacSecret) {
        this.jdbc = jdbc;
        this.billing = billing;
        this.credentials = credentials;
        this.accounts = accounts;
        this.contexts = contexts;
        this.paymentHmacSecret = paymentHmacSecret == null ? "" : paymentHmacSecret;
    }

    @GetMapping("/app/billing/wallet")
    public ApiBody<WalletBalanceView> wallet(
            @RequestParam("memberId") Long memberId,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bindRequestId(request, response);
        if (memberId == null || memberId <= 0) {
            throw BillingException.rejected("会员无效");
        }
        BoundMember bound = requireBoundMember(accounts.requireAccountId());
        if (bound.id() != memberId) {
            throw new BillingException("FORBIDDEN", 403, "无权查询该会员余额");
        }
        Money available = requireLedger().available(bound.id(), actor(bound.deptId(), request, requestId));
        return ApiBody.ok(new WalletBalanceView(bound.id(), available.decimal().toPlainString()), requestId);
    }

    @PostMapping("/app/billing/payments/notify")
    public ApiBody<PaymentAcceptedView> acceptPayment(
            @RequestHeader(value = AccountWebSupport.IDEMPOTENCY, required = false) String idempotencyHeader,
            @RequestBody PaymentNoticeRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bindRequestId(request, response);
        if (paymentHmacSecret.isBlank()) {
            throw new BillingException("BILLING_UNAVAILABLE", 503, "支付验签未配置");
        }
        if (body == null
                || body.provider() == null
                || body.outTradeNo() == null
                || body.amount() == null
                || body.memberId() == null
                || body.idempotencyKey() == null
                || body.signature() == null) {
            throw BillingException.rejected("支付回调参数无效");
        }
        if (idempotencyHeader == null || !idempotencyHeader.equals(body.idempotencyKey())) {
            throw BillingException.rejected("幂等键不一致");
        }
        Money amount;
        try {
            amount = Money.of(body.amount());
        } catch (IllegalArgumentException ex) {
            throw BillingException.rejected("金额无效");
        }
        String payload = body.provider() + "|" + body.outTradeNo() + "|"
                + amount.decimal().toPlainString() + "|" + body.memberId();
        new HmacPaymentVerifier(paymentHmacSecret).verify(payload, body.signature());
        BoundMember member = requireMember(body.memberId());
        String payloadHash = CanonicalHash.payment(body.provider(), body.outTradeNo(), amount, member.id());
        long ledgerId = requireLedger().credit(new CreditCommand(
                member.id(),
                amount,
                body.idempotencyKey(),
                payloadHash,
                body.provider(),
                body.outTradeNo(),
                body.providerTradeNo(),
                "payment.notify",
                actor(member.deptId(), request, requestId)));
        log.info("billing.payment.accepted memberId={} outTradeNo={}", member.id(), body.outTradeNo());
        return ApiBody.ok(new PaymentAcceptedView(ledgerId), requestId);
    }

    @PostMapping("/app/billing/byok-credentials")
    public ApiBody<ByokSavedView> saveByok(
            @RequestBody ByokCredentialRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestId = bindRequestId(request, response);
        if (body == null || body.provider() == null || body.endpointHost() == null) {
            throw BillingException.rejected("凭据参数无效");
        }
        SecretRef secretRef;
        try {
            secretRef = new SecretRef(body.secretRef());
        } catch (RuntimeException ex) {
            throw BillingException.rejected("secretRef rejected");
        }
        BoundMember bound = requireBoundMember(accounts.requireAccountId());
        ChannelCredentialStore store = credentials.getIfAvailable();
        if (store == null) {
            throw new BillingException("BILLING_UNAVAILABLE", 503, "凭据服务暂不可用");
        }
        long id;
        try {
            id = store.save(bound.id(), bound.deptId(), body.provider(), secretRef, body.endpointHost());
        } catch (IllegalArgumentException ex) {
            throw BillingException.rejected("凭据参数无效");
        }
        log.info("billing.byok.saved id={} memberId={} provider={}", id, bound.id(), body.provider());
        return ApiBody.ok(new ByokSavedView(id), requestId);
    }

    private JdbcBillingService requireLedger() {
        PlatformBilling port = billing.getIfAvailable();
        if (port instanceof JdbcBillingService jdbcBilling) {
            return jdbcBilling;
        }
        throw new BillingException("BILLING_UNAVAILABLE", 503, "计费服务暂不可用");
    }

    private BoundMember requireBoundMember(long accountId) {
        List<BoundMember> rows = jdbc.query(
                BOUND_MEMBER_SQL,
                (rs, rowNum) -> new BoundMember(rs.getLong("id"), rs.getLong("dept_id")),
                accountId);
        if (rows.isEmpty()) {
            throw new BillingException("FORBIDDEN", 403, "无权查询该会员余额");
        }
        return rows.getFirst();
    }

    private BoundMember requireMember(long memberId) {
        if (memberId <= 0) {
            throw BillingException.rejected("会员无效");
        }
        List<BoundMember> rows = jdbc.query(
                MEMBER_DEPT_SQL,
                (rs, rowNum) -> new BoundMember(rs.getLong("id"), rs.getLong("dept_id")),
                memberId);
        if (rows.isEmpty()) {
            throw BillingException.rejected("会员无效");
        }
        return rows.getFirst();
    }

    private BillingActor actor(long deptId, HttpServletRequest request, String requestId) {
        RequestContextPort.RequestContextSnapshot snapshot = snapshot();
        String traceId = snapshot == null ? request.getHeader("X-Trace-Id") : snapshot.traceId();
        String actorRequestId = snapshot == null || snapshot.requestId() == null || snapshot.requestId().isBlank()
                ? requestId
                : snapshot.requestId();
        return new BillingActor(deptId, false, traceId, actorRequestId);
    }

    private RequestContextPort.RequestContextSnapshot snapshot() {
        RequestContextPort port = contexts.getIfAvailable();
        if (port == null) {
            return null;
        }
        try {
            return port.current();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String bindRequestId(HttpServletRequest request, HttpServletResponse response) {
        String requestId = AccountWebSupport.requestId(request);
        response.setHeader(AccountWebSupport.REQUEST_ID, requestId);
        return requestId;
    }

    record BoundMember(long id, long deptId) {
    }

    public record WalletBalanceView(long memberId, String available) {
    }

    public record PaymentNoticeRequest(
            String provider,
            String outTradeNo,
            String amount,
            Long memberId,
            String idempotencyKey,
            String providerTradeNo,
            String signature) {
    }

    public record PaymentAcceptedView(long id) {
    }

    public record ByokCredentialRequest(String provider, String secretRef, String endpointHost) {
    }

    public record ByokSavedView(long id) {
    }
}

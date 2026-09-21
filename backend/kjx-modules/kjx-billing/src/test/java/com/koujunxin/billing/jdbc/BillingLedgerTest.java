package com.koujunxin.billing.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import com.koujunxin.billing.api.BillingActor;
import com.koujunxin.billing.api.BillingException;
import com.koujunxin.billing.api.BillingOutboxPort;
import com.koujunxin.billing.api.CreditCommand;
import com.koujunxin.billing.api.FreezeCommand;
import com.koujunxin.billing.api.SettleCommand;
import com.koujunxin.billing.ledger.CanonicalHash;
import com.koujunxin.billing.ledger.Money;
import com.koujunxin.billing.payment.HmacPaymentVerifier;
import com.koujunxin.billing.payment.PaymentQueryPort;
import com.koujunxin.channel.api.SecretRef;
import com.koujunxin.channel.jdbc.JdbcChannelCredentialStore;

class BillingLedgerTest extends MysqlBillingSupport {

    private final BillingActor actor = new BillingActor(11L, false, "trace-bill", "req-bill");
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void clean() {
        resetTables();
        events.clear();
    }

    @Test
    void duplicateCallbackCreditsOnceAndConflictDoesNotPost() {
        JdbcBillingService billing = service(recordingOutbox(events), idempotencyKey -> {
            throw new IllegalStateException("clickhouse down");
        });
        HmacPaymentVerifier verifier = new HmacPaymentVerifier("sandbox-sign-key");
        CreditCommand first = credit("pay-1", Money.of("10.000000"), "trade-1");
        verifier.verify(payload(first), verifier.sign(payload(first)));
        long ledgerId = billing.credit(first);
        long replay = billing.credit(first);
        assertEquals(ledgerId, replay);
        assertEquals(Money.of("10.000000"), billing.available(3L, actor));
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM kjx_ledger_entry", Integer.class));
        CreditCommand tampered = credit("pay-1", Money.of("11.000000"), "trade-1");
        BillingException conflict = assertThrows(BillingException.class, () -> billing.credit(tampered));
        assertEquals(1090, conflict.code());
        assertEquals(Money.of("10.000000"), billing.available(3L, actor));
        assertTrue(events.contains("payment.paid"));
    }

    @Test
    void settleRollbackLeavesNoDriftAndUsageMatchesLedger() {
        JdbcBillingService billing = service(recordingOutbox(events), idempotencyKey -> {
        });
        billing.credit(credit("pay-2", Money.of("4.000000"), "trade-2"));
        long holdId = billing.freeze(freeze(Money.of("4.000000"), "freeze-1"));
        long ledgerId = billing.settle(settle(holdId, Money.of("1.500000"), "settle-1"));
        assertTrue(ledgerId > 0);
        assertEquals(Money.ZERO, billing.reconcileUsage(3L, actor));
        assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM kjx_reconcile_record", Integer.class));

        JdbcBillingService failing = service((aggregateType, aggregateId, eventType, idempotencyKey, payload) -> {
            if ("billing.settled".equals(eventType)) {
                throw new IllegalStateException("outbox down");
            }
            events.add(eventType);
        }, idempotencyKey -> {
        });
        long secondHold = failing.freeze(freeze(Money.of("1.000000"), "freeze-2"));
        assertThrows(IllegalStateException.class, () -> failing.settle(settle(secondHold, Money.of("1.000000"), "settle-2")));
        assertEquals("OPEN", jdbc().queryForObject("SELECT status FROM kjx_balance_hold WHERE id = ?", String.class, secondHold));
        assertEquals(0, jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_ledger_entry WHERE idempotency_key = 'settle-2'", Integer.class));
        assertThrows(DataAccessException.class, () -> jdbc().update("DELETE FROM kjx_ledger_entry WHERE id = ?", ledgerId));
    }

    @Test
    void paymentDriftIsRecordedFromProviderQuery() {
        JdbcBillingService billing = service(recordingOutbox(events), idempotencyKey -> {
        });
        billing.credit(credit("pay-3", Money.of("8.000000"), "trade-3"));
        PaymentQueryPort query = (provider, outTradeNo) -> Money.of("8.000000");
        assertEquals(Money.ZERO, billing.reconcilePayment(3L, actor, query));
        PaymentQueryPort drifted = (provider, outTradeNo) -> Money.of("7.000000");
        assertEquals(Money.of("1.000000"), billing.reconcilePayment(3L, actor, drifted));
        assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM kjx_reconcile_record", Integer.class));
    }

    @Test
    void credentialStoreKeepsOnlySecretRef() {
        JdbcChannelCredentialStore store = new JdbcChannelCredentialStore(jdbc());
        long id = store.save(3L, 11L, "OPENAI", new SecretRef("ref:member.3"), "api.example.com");
        String stored = jdbc().queryForObject("SELECT secret_ref FROM kjx_channel_credential WHERE id = ?", String.class, id);
        assertEquals("ref:member.3", stored);
        assertEquals(0, jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE()
                   AND table_name = 'kjx_channel_credential'
                   AND column_name IN ('api_key','secret','token')
                """,
                Integer.class));
    }

    @Test
    void otherDepartmentCannotFreeze() {
        JdbcBillingService billing = service(recordingOutbox(events), idempotencyKey -> {
        });
        billing.credit(credit("pay-4", Money.of("3.000000"), "trade-4"));
        BillingActor intruder = new BillingActor(99L, false, "trace", "request");
        assertThrows(BillingException.class, () -> billing.freeze(new FreezeCommand(
                3L, Money.of("1.000000"), "freeze-x", hash("freeze-x"), "cross", 9L, intruder)));
    }

    private FreezeCommand freeze(Money amount, String key) {
        return new FreezeCommand(3L, amount, key, hash(key), "estimate", 9L, actor);
    }

    private SettleCommand settle(long holdId, Money actual, String key) {
        return new SettleCommand(holdId, actual, key, hash(key + actual.decimal().toPlainString()), 10, 2, "sandbox", actor);
    }

    private CreditCommand credit(String key, Money amount, String tradeNo) {
        String hash = CanonicalHash.payment("ALIPAY", tradeNo, amount, 3L);
        return new CreditCommand(3L, amount, key, hash, "ALIPAY", tradeNo, "provider-" + tradeNo, "sandbox-pay", actor);
    }

    private String payload(CreditCommand command) {
        return command.provider() + "|" + command.outTradeNo() + "|" + command.amount().decimal().toPlainString() + "|" + command.memberId();
    }

    private static String hash(String value) {
        return CanonicalHash.sha256(value);
    }
}

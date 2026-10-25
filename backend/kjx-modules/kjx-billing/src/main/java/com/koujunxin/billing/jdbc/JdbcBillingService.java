package com.koujunxin.billing.jdbc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.koujunxin.billing.api.BillingActor;
import com.koujunxin.billing.api.BillingAuditPort;
import com.koujunxin.billing.api.BillingException;
import com.koujunxin.billing.api.BillingOutboxPort;
import com.koujunxin.billing.api.BillingProjection;
import com.koujunxin.billing.api.CreditCommand;
import com.koujunxin.billing.api.FreezeCommand;
import com.koujunxin.billing.api.PlatformBilling;
import com.koujunxin.billing.api.ReleaseCommand;
import com.koujunxin.billing.api.SettleCommand;
import com.koujunxin.billing.ledger.BalanceMath;
import com.koujunxin.billing.ledger.IdempotencyDecider;
import com.koujunxin.billing.ledger.Money;
import com.koujunxin.billing.payment.PaymentQueryPort;

public final class JdbcBillingService implements PlatformBilling {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final BillingOutboxPort outbox;
    private final BillingAuditPort audit;
    private final BillingProjection projection;

    public JdbcBillingService(
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            BillingOutboxPort outbox,
            BillingAuditPort audit,
            BillingProjection projection) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.outbox = outbox;
        this.audit = audit;
        this.projection = projection == null ? BillingProjection.noop() : projection;
    }

    @Override
    public long freeze(FreezeCommand command) {
        requireKey(command.idempotencyKey(), command.payloadHash(), command.reason());
        requirePositive(command.amount());
        return tx.execute(status -> {
            Long existing = findHold(command.idempotencyKey());
            if (existing != null) {
                return replayHold(existing, command.payloadHash());
            }
            Wallet wallet = lockWallet(command.memberId(), command.actor());
            Money available = BalanceMath.available(ledgerNet(command.memberId()), openHolds(command.memberId()));
            if (!BalanceMath.covers(available, command.amount())) {
                throw BillingException.insufficient();
            }
            try {
                jdbc.update(
                        """
                        INSERT INTO kjx_balance_hold
                          (member_id, dept_id, amount, status, idempotency_key, payload_hash, reason, work_id)
                        VALUES (?, ?, ?, 'OPEN', ?, ?, ?, ?)
                        """,
                        command.memberId(),
                        wallet.deptId(),
                        command.amount().decimal(),
                        command.idempotencyKey(),
                        command.payloadHash(),
                        command.reason(),
                        command.workId());
            } catch (DuplicateKeyException ex) {
                return replayHold(findHold(command.idempotencyKey()), command.payloadHash());
            }
            Long holdId = findHold(command.idempotencyKey());
            emit("billing", String.valueOf(holdId), "billing.frozen", command.idempotencyKey(), command.memberId(), command.amount());
            audit("FREEZE", "HOLD", command.memberId(), command.amount(), command.idempotencyKey(), command.actor());
            return holdId;
        });
    }

    @Override
    public long settle(SettleCommand command) {
        requireKey(command.idempotencyKey(), command.payloadHash(), "settle");
        if (command.actual() == null || command.inputTokens() < 0 || command.outputTokens() < 0) {
            throw BillingException.rejected("结算参数无效");
        }
        return tx.execute(status -> {
            Hold hold = lockHold(command.holdId());
            lockWallet(hold.memberId(), command.actor());
            Long existingLedger = findLedger(command.idempotencyKey());
            if (existingLedger != null) {
                return replayLedger(existingLedger, command.payloadHash());
            }
            if (!"OPEN".equals(hold.status())) {
                throw BillingException.rejected("冻结单不可结算");
            }
            if (command.actual().compareTo(hold.amount()) > 0) {
                throw BillingException.rejected("结算金额超过冻结");
            }
            Money balanceAfter = ledgerNet(hold.memberId()).minus(command.actual());
            jdbc.update(
                    """
                    INSERT INTO kjx_ledger_entry
                      (member_id, dept_id, biz_type, direction, amount, balance_after, ref_type, ref_id,
                       idempotency_key, payload_hash, reason)
                    VALUES (?, ?, 'AI_CHARGE', 'OUT', ?, ?, 'hold', ?, ?, ?, 'settle')
                    """,
                    hold.memberId(),
                    hold.deptId(),
                    command.actual().decimal(),
                    balanceAfter.decimal(),
                    hold.id(),
                    command.idempotencyKey(),
                    command.payloadHash());
            jdbc.update(
                    """
                    INSERT INTO kjx_usage_event
                      (member_id, dept_id, work_id, channel, model_name, input_tokens, output_tokens, cost, ledger_idempotency_key)
                    VALUES (?, ?, ?, 'PLATFORM', ?, ?, ?, ?, ?)
                    """,
                    hold.memberId(),
                    hold.deptId(),
                    hold.workId(),
                    command.model() == null ? "unspecified" : command.model(),
                    command.inputTokens(),
                    command.outputTokens(),
                    command.actual().decimal(),
                    command.idempotencyKey());
            jdbc.update("UPDATE kjx_balance_hold SET status = 'SETTLED' WHERE id = ? AND status = 'OPEN'", hold.id());
            Long ledgerId = findLedger(command.idempotencyKey());
            emit("billing", String.valueOf(ledgerId), "billing.settled", command.idempotencyKey(), hold.memberId(), command.actual());
            audit("AI_CHARGE", "OUT", hold.memberId(), command.actual(), command.idempotencyKey(), command.actor());
            afterCommit(command.idempotencyKey());
            return ledgerId;
        });
    }

    @Override
    public void release(ReleaseCommand command) {
        requireReason(command.reason());
        tx.executeWithoutResult(status -> {
            Hold hold = lockHold(command.holdId());
            lockWallet(hold.memberId(), command.actor());
            if ("RELEASED".equals(hold.status()) || "SETTLED".equals(hold.status())) {
                return;
            }
            if (!"OPEN".equals(hold.status())) {
                throw BillingException.rejected("冻结单不可释放");
            }
            jdbc.update("UPDATE kjx_balance_hold SET status = 'RELEASED' WHERE id = ? AND status = 'OPEN'", hold.id());
            emit("billing", String.valueOf(hold.id()), "billing.released", "release:" + hold.id(), hold.memberId(), hold.amount());
            audit("RELEASE", "HOLD", hold.memberId(), hold.amount(), "release:" + hold.id(), command.actor());
        });
    }

    public long credit(CreditCommand command) {
        requireKey(command.idempotencyKey(), command.payloadHash(), command.reason());
        requirePositive(command.amount());
        requireProvider(command.provider());
        return tx.execute(status -> {
            Wallet wallet = lockWallet(command.memberId(), command.actor());
            Long existing = findLedger(command.idempotencyKey());
            if (existing != null) {
                return replayLedger(existing, command.payloadHash());
            }
            Money balanceAfter = ledgerNet(command.memberId()).plus(command.amount());
            jdbc.update(
                    """
                    INSERT INTO kjx_ledger_entry
                      (member_id, dept_id, biz_type, direction, amount, balance_after, ref_type, ref_id,
                       idempotency_key, payload_hash, reason)
                    VALUES (?, ?, 'RECHARGE', 'IN', ?, ?, 'order', ?, ?, ?, ?)
                    """,
                    command.memberId(),
                    wallet.deptId(),
                    command.amount().decimal(),
                    balanceAfter.decimal(),
                    command.outTradeNo(),
                    command.idempotencyKey(),
                    command.payloadHash(),
                    command.reason());
            jdbc.update(
                    """
                    INSERT INTO kjx_pay_order
                      (member_id, dept_id, provider, out_trade_no, amount, status, idempotency_key, payload_hash, provider_trade_no)
                    VALUES (?, ?, ?, ?, ?, 'PAID', ?, ?, ?)
                    """,
                    command.memberId(),
                    wallet.deptId(),
                    command.provider(),
                    command.outTradeNo(),
                    command.amount().decimal(),
                    command.idempotencyKey(),
                    command.payloadHash(),
                    command.providerTradeNo());
            Long ledgerId = findLedger(command.idempotencyKey());
            emit("payment", command.outTradeNo(), "payment.paid", command.idempotencyKey(), command.memberId(), command.amount());
            audit("RECHARGE", "IN", command.memberId(), command.amount(), command.idempotencyKey(), command.actor());
            afterCommit(command.idempotencyKey());
            return ledgerId;
        });
    }

    public Money available(long memberId, BillingActor actor) {
        return tx.execute(status -> {
            lockWallet(memberId, actor);
            return BalanceMath.available(ledgerNet(memberId), openHolds(memberId));
        });
    }

    public Money reconcileUsage(long memberId, BillingActor actor) {
        return tx.execute(status -> {
            Wallet wallet = lockWallet(memberId, actor);
            Money ledger = sum("""
                    SELECT COALESCE(SUM(amount), 0) FROM kjx_ledger_entry
                     WHERE member_id = ? AND biz_type = 'AI_CHARGE' AND direction = 'OUT'
                    """, memberId);
            Money usage = sum("""
                    SELECT COALESCE(SUM(cost), 0) FROM kjx_usage_event WHERE member_id = ?
                    """, memberId);
            Money drift = ledger.micros() >= usage.micros()
                    ? ledger.minus(usage)
                    : usage.minus(ledger);
            if (drift.micros() != 0) {
                jdbc.update(
                        """
                        INSERT INTO kjx_reconcile_record
                          (scope_type, scope_id, dept_id, expected_amount, actual_amount, drift, status, detail)
                        VALUES ('USAGE', ?, ?, ?, ?, ?, 'OPEN', 'ledger and usage differ')
                        """,
                        String.valueOf(memberId),
                        wallet.deptId(),
                        usage.decimal(),
                        ledger.decimal(),
                        drift.decimal());
            }
            return drift;
        });
    }

    public Money reconcilePayment(long memberId, BillingActor actor, PaymentQueryPort query) {
        return tx.execute(status -> {
            Wallet wallet = lockWallet(memberId, actor);
            List<OrderRow> orders = jdbc.query(
                    """
                    SELECT provider, out_trade_no, amount FROM kjx_pay_order
                     WHERE member_id = ? AND status = 'PAID'
                    """,
                    (rs, row) -> new OrderRow(rs.getString("provider"), rs.getString("out_trade_no"), Money.of(rs.getBigDecimal("amount"))),
                    memberId);
            Money drift = Money.ZERO;
            for (OrderRow order : orders) {
                Money remote = query.queryPaidAmount(order.provider(), order.outTradeNo());
                if (remote.micros() == order.amount().micros()) {
                    continue;
                }
                Money rowDrift = remote.micros() >= order.amount().micros()
                        ? remote.minus(order.amount())
                        : order.amount().minus(remote);
                drift = drift.plus(rowDrift);
                jdbc.update(
                        """
                        INSERT INTO kjx_reconcile_record
                          (scope_type, scope_id, dept_id, expected_amount, actual_amount, drift, status, detail)
                        VALUES ('PAYMENT', ?, ?, ?, ?, ?, 'OPEN', 'provider amount differs')
                        """,
                        order.outTradeNo(),
                        wallet.deptId(),
                        order.amount().decimal(),
                        remote.decimal(),
                        rowDrift.decimal());
            }
            return drift;
        });
    }

    private Long replayHold(Long holdId, String payloadHash) {
        if (holdId == null) {
            throw BillingException.conflict();
        }
        String stored = jdbc.queryForObject(
                "SELECT payload_hash FROM kjx_balance_hold WHERE id = ?",
                String.class,
                holdId);
        if (IdempotencyDecider.decide(stored, payloadHash) == IdempotencyDecider.Kind.CONFLICT) {
            throw BillingException.conflict();
        }
        return holdId;
    }

    private Long replayLedger(Long ledgerId, String payloadHash) {
        String stored = jdbc.queryForObject(
                "SELECT payload_hash FROM kjx_ledger_entry WHERE id = ?",
                String.class,
                ledgerId);
        if (IdempotencyDecider.decide(stored, payloadHash) == IdempotencyDecider.Kind.CONFLICT) {
            throw BillingException.conflict();
        }
        return ledgerId;
    }

    private Wallet lockWallet(long memberId, BillingActor actor) {
        if (memberId <= 0) {
            throw BillingException.rejected("会员无效");
        }
        List<Wallet> rows = jdbc.query(
                "SELECT member_id, dept_id FROM kjx_wallet WHERE member_id = ? FOR UPDATE",
                (rs, row) -> new Wallet(rs.getLong("member_id"), rs.getLong("dept_id")),
                memberId);
        if (rows.isEmpty()) {
            try {
                jdbc.update("INSERT INTO kjx_wallet (member_id, dept_id) VALUES (?, ?)", memberId, actor.deptId());
            } catch (DuplicateKeyException ignored) {
                // 并发首建由下方重新加锁。
            }
            rows = jdbc.query(
                    "SELECT member_id, dept_id FROM kjx_wallet WHERE member_id = ? FOR UPDATE",
                    (rs, row) -> new Wallet(rs.getLong("member_id"), rs.getLong("dept_id")),
                    memberId);
        }
        Wallet wallet = rows.get(0);
        assertDept(wallet.deptId(), actor);
        return wallet;
    }

    private Hold lockHold(long holdId) {
        List<Hold> rows = jdbc.query(
                """
                SELECT id, member_id, dept_id, amount, status, work_id
                  FROM kjx_balance_hold WHERE id = ? FOR UPDATE
                """,
                (rs, row) -> new Hold(
                        rs.getLong("id"),
                        rs.getLong("member_id"),
                        rs.getLong("dept_id"),
                        Money.of(rs.getBigDecimal("amount")),
                        rs.getString("status"),
                        rs.getObject("work_id") == null ? null : rs.getLong("work_id")),
                holdId);
        if (rows.isEmpty()) {
            throw BillingException.rejected("冻结单不存在");
        }
        return rows.get(0);
    }

    private void assertDept(long deptId, BillingActor actor) {
        if (!actor.ops() && actor.deptId() != deptId) {
            throw BillingException.forbidden();
        }
    }

    private Money ledgerNet(long memberId) {
        BigDecimal value = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(CASE direction WHEN 'IN' THEN amount ELSE -amount END), 0)
                  FROM kjx_ledger_entry WHERE member_id = ?
                """,
                BigDecimal.class,
                memberId);
        return Money.of(value);
    }

    private Money openHolds(long memberId) {
        return sum("SELECT COALESCE(SUM(amount), 0) FROM kjx_balance_hold WHERE member_id = ? AND status = 'OPEN'", memberId);
    }

    private Money sum(String sql, long memberId) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, memberId);
        return Money.of(value);
    }

    private Long findHold(String idempotencyKey) {
        List<Long> ids = jdbc.query(
                "SELECT id FROM kjx_balance_hold WHERE idempotency_key = ?",
                (rs, row) -> rs.getLong("id"),
                idempotencyKey);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long findLedger(String idempotencyKey) {
        List<Long> ids = jdbc.query(
                "SELECT id FROM kjx_ledger_entry WHERE idempotency_key = ?",
                (rs, row) -> rs.getLong("id"),
                idempotencyKey);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void emit(String aggregateType, String aggregateId, String eventType, String idempotencyKey, long memberId, Money amount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("memberId", memberId);
        payload.put("amount", amount.decimal().toPlainString());
        outbox.append(aggregateType, aggregateId, eventType, idempotencyKey, payload);
    }

    private void audit(String bizType, String direction, long memberId, Money amount, String idempotencyKey, BillingActor actor) {
        audit.append(memberId, bizType, direction, amount.decimal(), idempotencyKey, actor.traceId(), actor.requestId());
    }

    private void afterCommit(String idempotencyKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safely(idempotencyKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safely(idempotencyKey);
            }
        });
    }

    private void safely(String idempotencyKey) {
        try {
            projection.publish(idempotencyKey);
        } catch (RuntimeException ignored) {
            // ClickHouse 或分析投影失败不得回写账本。
        }
    }

    private static void requirePositive(Money amount) {
        if (amount == null || amount.micros() <= 0) {
            throw BillingException.rejected("金额必须大于 0");
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw BillingException.rejected("reason 必填");
        }
    }

    private static void requireKey(String idempotencyKey, String payloadHash, String reason) {
        requireReason(reason);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw BillingException.rejected("幂等键无效");
        }
        if (payloadHash == null || !payloadHash.matches("[0-9a-f]{64}")) {
            throw BillingException.rejected("请求摘要无效");
        }
    }

    private static void requireProvider(String provider) {
        if (!"ALIPAY".equals(provider) && !"WECHAT".equals(provider)) {
            throw BillingException.rejected("支付渠道无效");
        }
    }

    private record Wallet(long memberId, long deptId) {
    }

    private record Hold(long id, long memberId, long deptId, Money amount, String status, Long workId) {
    }

    private record OrderRow(String provider, String outTradeNo, Money amount) {
    }
}

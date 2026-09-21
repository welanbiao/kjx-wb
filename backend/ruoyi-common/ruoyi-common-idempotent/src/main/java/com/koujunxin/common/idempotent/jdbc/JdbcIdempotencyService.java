package com.koujunxin.common.idempotent.jdbc;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import com.koujunxin.common.idempotent.IdempotencyBegin;
import com.koujunxin.common.idempotent.IdempotencyCommand;
import com.koujunxin.common.idempotent.IdempotencyConflictException;
import com.koujunxin.common.idempotent.IdempotencyRef;
import com.koujunxin.common.idempotent.IdempotencyResult;
import com.koujunxin.common.idempotent.IdempotencyService;

/**
 * Spring JDBC 实现。INSERT 占位使用当前事务；DuplicateKey 后读取并比较 request_hash。
 */
public class JdbcIdempotencyService implements IdempotencyService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final String INSERT_SQL = """
            INSERT INTO kjx_idempotency_guard
              (scene, ref_id, idempotency_key, actor_id, request_hash, status, expire_at)
            VALUES (?, ?, ?, ?, ?, 'PROCESSING', ?)
            """;

    private static final String SELECT_SQL = """
            SELECT scene, ref_id, idempotency_key, request_hash, status, result_code, result_body, expire_at
              FROM kjx_idempotency_guard
             WHERE scene = ? AND ref_id = ? AND idempotency_key = ?
            """;

    private static final String COMPLETE_SQL = """
            UPDATE kjx_idempotency_guard
               SET status = ?, result_code = ?, result_body = CAST(? AS JSON)
             WHERE scene = ? AND ref_id = ? AND idempotency_key = ?
               AND status = 'PROCESSING'
            """;

    private static final String RECLAIM_SQL = """
            UPDATE kjx_idempotency_guard
               SET actor_id = ?, request_hash = ?, status = 'PROCESSING',
                   result_code = NULL, result_body = NULL, expire_at = ?
             WHERE scene = ? AND ref_id = ? AND idempotency_key = ?
               AND status = 'PROCESSING'
               AND expire_at < ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcIdempotencyService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.system(SHANGHAI));
    }

    public JdbcIdempotencyService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public IdempotencyBegin begin(IdempotencyCommand command) {
        requireActiveTransaction();
        LocalDateTime expireAt = now().plus(command.ttl());
        CannotAcquireLockException lastLock = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                jdbcTemplate.update(INSERT_SQL,
                        command.scene(),
                        command.refId(),
                        command.idempotencyKey(),
                        command.actorId(),
                        command.requestHash(),
                        Timestamp.valueOf(expireAt));
                return new IdempotencyBegin.FirstAttempt();
            } catch (DuplicateKeyException ex) {
                GuardRow row = load(command.ref());
                if (row == null) {
                    continue;
                }
                return handleExisting(command, row);
            } catch (CannotAcquireLockException ex) {
                lastLock = ex;
            }
        }
        if (lastLock != null) {
            throw lastLock;
        }
        throw new IllegalStateException("duplicate key but guard row missing");
    }

    @Override
    public void completeSuccess(IdempotencyRef ref, IdempotencyResult result) {
        complete(ref, "SUCCEEDED", result);
    }

    @Override
    public void completeFailure(IdempotencyRef ref, IdempotencyResult result) {
        complete(ref, "FAILED", result);
    }

    private void complete(IdempotencyRef ref, String status, IdempotencyResult result) {
        requireActiveTransaction();
        Assert.notNull(result, "result is required");
        int updated = jdbcTemplate.update(COMPLETE_SQL,
                status,
                result.resultCode(),
                result.resultBodyJson(),
                ref.scene(),
                ref.refId(),
                ref.idempotencyKey());
        if (updated == 0) {
            GuardRow existing = load(ref);
            if (existing == null) {
                throw new IllegalStateException("idempotency guard not found");
            }
            if (status.equals(existing.status())
                    && result.resultCode().equals(existing.resultCode())) {
                return;
            }
            throw new IllegalStateException("idempotency guard is not PROCESSING");
        }
    }

    private IdempotencyBegin handleExisting(IdempotencyCommand command, GuardRow row) {
        if (!row.requestHash().equalsIgnoreCase(command.requestHash())) {
            throw new IdempotencyConflictException(command.ref());
        }
        if ("SUCCEEDED".equals(row.status()) || "FAILED".equals(row.status())) {
            return new IdempotencyBegin.Replay(new IdempotencyResult(row.resultCode(), row.resultBody()));
        }
        if ("PROCESSING".equals(row.status()) && row.expireAt().isBefore(now())) {
            int reclaimed = jdbcTemplate.update(RECLAIM_SQL,
                    command.actorId(),
                    command.requestHash(),
                    Timestamp.valueOf(now().plus(command.ttl())),
                    command.scene(),
                    command.refId(),
                    command.idempotencyKey(),
                    Timestamp.valueOf(now()));
            if (reclaimed == 1) {
                return new IdempotencyBegin.FirstAttempt();
            }
        }
        return new IdempotencyBegin.InProgress();
    }

    private GuardRow load(IdempotencyRef ref) {
        return jdbcTemplate.query(SELECT_SQL, rs -> {
            if (!rs.next()) {
                return null;
            }
            Timestamp expire = rs.getTimestamp("expire_at");
            return new GuardRow(
                    rs.getString("request_hash"),
                    rs.getString("status"),
                    rs.getString("result_code"),
                    rs.getString("result_body"),
                    expire == null ? LocalDateTime.MIN : expire.toLocalDateTime());
        }, ref.scene(), ref.refId(), ref.idempotencyKey());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), SHANGHAI);
    }

    private static void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("IdempotencyService requires an active transaction");
        }
    }

    private record GuardRow(
            String requestHash,
            String status,
            String resultCode,
            String resultBody,
            LocalDateTime expireAt
    ) {
    }
}

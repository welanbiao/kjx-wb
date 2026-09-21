package com.koujunxin.log.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.koujunxin.log.api.port.BillingLogWritePort;
import com.koujunxin.log.api.port.BizLogWritePort;
import com.koujunxin.log.support.MysqlLogSupport;

class MysqlAuditTransactionTest extends MysqlLogSupport {

    @BeforeEach
    void setUp() {
        ensureMigrated();
        truncateLogs();
    }

    @Test
    void auditBizBillingRollbackWithBusiness() {
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            auditWriter().append(auditCommand());
            bizWriter().append(new BizLogWritePort.BizLogCommand(
                    UUID.randomUUID().toString(), Instant.now(), "trace-log", "req-log",
                    "ADMIN", 1L, "work-1", "UNLOCK", "SUCCESS", "ok", Map.of("before", 1)));
            billingWriter().append(new BillingLogWritePort.BillingLogCommand(
                    UUID.randomUUID().toString(), Instant.now(), "trace-log", "req-log",
                    1L, "ledger", "DEBIT", new BigDecimal("1.00"), "idem-1", Map.of("ref", "a")));
            throw new IllegalStateException("business boom");
        })).hasMessageContaining("business boom");

        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM log_audit", Long.class)).isZero();
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM log_biz", Long.class)).isZero();
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM log_billing", Long.class)).isZero();
    }

    @Test
    void auditPersistsWhenTransactionCommits() {
        tx().executeWithoutResult(status -> auditWriter().append(auditCommand()));
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM log_audit", Long.class)).isEqualTo(1);
        String hash = jdbc().queryForObject("SELECT hash FROM log_audit", String.class);
        String prev = jdbc().queryForObject("SELECT prev_hash FROM log_audit", String.class);
        assertThat(prev).isEqualTo(AuditChainHasher.GENESIS);
        assertThat(hash).hasSize(64).isNotEqualTo(prev);
    }

    @Test
    void auditRequiresActiveTransaction() {
        assertThatThrownBy(() -> auditWriter().append(auditCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }
}

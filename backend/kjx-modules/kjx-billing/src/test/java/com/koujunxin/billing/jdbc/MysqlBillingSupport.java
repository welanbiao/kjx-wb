package com.koujunxin.billing.jdbc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.koujunxin.billing.api.BillingAuditPort;
import com.koujunxin.billing.api.BillingOutboxPort;
import com.koujunxin.billing.api.BillingProjection;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Testcontainers(disabledWithoutDocker = true)
abstract class MysqlBillingSupport {

    static final MySQLContainer<?> MYSQL = start();

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;

    private static MySQLContainer<?> start() {
        MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("kjx_billing")
                .withUsername("kjx")
                .withPassword("kjx")
                .withCommand(
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_0900_ai_ci",
                        "--log-bin-trust-function-creators=1");
        mysql.start();
        Runtime.getRuntime().addShutdownHook(new Thread(mysql::stop));
        return mysql;
    }

    protected static synchronized void ensureMigrated() {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setMaximumPoolSize(8);
        config.setConnectionTimeout(Duration.ofSeconds(30).toMillis());
        dataSource = new HikariDataSource(config);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    protected JdbcTemplate jdbc() {
        ensureMigrated();
        return jdbcTemplate;
    }

    protected JdbcBillingService service(BillingOutboxPort outbox, BillingProjection projection) {
        ensureMigrated();
        List<String> audits = new ArrayList<>();
        BillingAuditPort audit = (memberId, bizType, direction, amount, idempotencyKey, traceId, requestId) ->
                audits.add(bizType + ":" + idempotencyKey);
        return new JdbcBillingService(jdbcTemplate, transactionTemplate, outbox, audit, projection);
    }

    protected BillingOutboxPort recordingOutbox(List<String> events) {
        return (aggregateType, aggregateId, eventType, idempotencyKey, payload) -> events.add(eventType);
    }

    protected void resetTables() {
        jdbc().execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc().execute("TRUNCATE TABLE kjx_reconcile_record");
        jdbc().execute("TRUNCATE TABLE kjx_pay_order");
        jdbc().execute("TRUNCATE TABLE kjx_usage_event");
        jdbc().execute("TRUNCATE TABLE kjx_ledger_entry");
        jdbc().execute("TRUNCATE TABLE kjx_balance_hold");
        jdbc().execute("TRUNCATE TABLE kjx_wallet");
        jdbc().execute("TRUNCATE TABLE kjx_channel_credential");
        jdbc().execute("SET FOREIGN_KEY_CHECKS=1");
    }
}

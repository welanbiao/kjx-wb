package com.koujunxin.log.support;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.koujunxin.log.api.port.AuditLogWritePort;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.context.MdcRequestContextPort;
import com.koujunxin.log.mysql.JdbcAuditLogWriter;
import com.koujunxin.log.mysql.JdbcBillingLogWriter;
import com.koujunxin.log.mysql.JdbcBizLogWriter;
import com.koujunxin.log.projection.ClickHouseProjectionPublisher;
import com.koujunxin.log.projection.OutboxAnalyticsProjectionPort;
import com.koujunxin.log.redact.LogRedactor;
import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxProperties;
import com.koujunxin.notification.outbox.OutboxPublisher;
import com.koujunxin.notification.outbox.OutboxRepository;
import com.koujunxin.notification.outbox.OutboxWriter;
import com.koujunxin.notification.outbox.jdbc.JdbcConsumerIdempotencyStore;
import com.koujunxin.notification.outbox.jdbc.JdbcOutboxRepository;
import com.koujunxin.notification.outbox.jdbc.JdbcOutboxWorker;
import com.koujunxin.notification.outbox.jdbc.JdbcOutboxWriter;
import com.koujunxin.notification.outbox.validation.OutboxPayloadValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Testcontainers(disabledWithoutDocker = true)
public abstract class MysqlLogSupport {

    static final MySQLContainer<?> MYSQL = createAndStart();

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;
    private static ObjectMapper objectMapper;
    private static OutboxPayloadValidator validator;
    private static LogRedactor redactor;
    private static String jdbcUrl;

    private static MySQLContainer<?> createAndStart() {
        MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("kjx_log")
                .withUsername("kjx")
                .withPassword("kjx")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");
        mysql.start();
        Runtime.getRuntime().addShutdownHook(new Thread(mysql::stop));
        return mysql;
    }

    protected static synchronized void ensureMigrated() {
        if (dataSource != null && MYSQL.getJdbcUrl().equals(jdbcUrl) && !dataSource.isClosed()) {
            return;
        }
        if (dataSource != null) {
            dataSource.close();
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(Duration.ofSeconds(30).toMillis());
        config.setConnectionInitSql("SET time_zone = '+08:00'");
        dataSource = new HikariDataSource(config);
        jdbcUrl = MYSQL.getJdbcUrl();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        PlatformTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        transactionTemplate = new TransactionTemplate(txManager);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        validator = new OutboxPayloadValidator(objectMapper);
        redactor = new LogRedactor();
    }

    protected JdbcTemplate jdbc() {
        ensureMigrated();
        return jdbcTemplate;
    }

    protected TransactionTemplate tx() {
        ensureMigrated();
        return transactionTemplate;
    }

    protected ObjectMapper mapper() {
        ensureMigrated();
        return objectMapper;
    }

    protected LogRedactor redactor() {
        ensureMigrated();
        return redactor;
    }

    protected JdbcAuditLogWriter auditWriter() {
        ensureMigrated();
        return new JdbcAuditLogWriter(jdbcTemplate, objectMapper, redactor);
    }

    protected JdbcBizLogWriter bizWriter() {
        ensureMigrated();
        return new JdbcBizLogWriter(jdbcTemplate, objectMapper, redactor);
    }

    protected JdbcBillingLogWriter billingWriter() {
        ensureMigrated();
        return new JdbcBillingLogWriter(jdbcTemplate, objectMapper, redactor);
    }

    protected OutboxWriter outboxWriter() {
        ensureMigrated();
        return new JdbcOutboxWriter(jdbcTemplate, objectMapper, validator);
    }

    protected OutboxRepository outboxRepository() {
        ensureMigrated();
        return new JdbcOutboxRepository(jdbcTemplate);
    }

    protected JdbcConsumerIdempotencyStore consumeStore() {
        ensureMigrated();
        return new JdbcConsumerIdempotencyStore(jdbcTemplate);
    }

    protected OutboxAnalyticsProjectionPort analyticsPort() {
        ensureMigrated();
        RequestContextPort.RequestContextSnapshot snapshot =
                RequestContextPort.RequestContextSnapshot.of("req-log", "trace-log");
        MdcRequestContextPort port = new MdcRequestContextPort();
        port.bind(snapshot);
        return new OutboxAnalyticsProjectionPort(outboxWriter(), port, redactor);
    }

    protected JdbcOutboxWorker worker(OutboxPublisher publisher, OutboxProperties properties) {
        return new JdbcOutboxWorker(outboxRepository(), publisher, tx(), properties, mapper());
    }

    protected ClickHouseProjectionPublisher projectionPublisher(
            com.koujunxin.log.projection.ClickHouseProjectionSink sink) {
        return new ClickHouseProjectionPublisher(
                consumeStore(), sink, new com.koujunxin.log.metrics.NoOpObservabilityMetrics(), redactor());
    }

    protected void truncateLogs() {
        jdbc().execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc().execute("TRUNCATE TABLE log_projection_reconcile");
        jdbc().execute("TRUNCATE TABLE log_audit");
        jdbc().execute("TRUNCATE TABLE log_biz");
        jdbc().execute("TRUNCATE TABLE log_billing");
        jdbc().execute("TRUNCATE TABLE log_sec");
        jdbc().execute("TRUNCATE TABLE kjx_outbox_consume_receipt");
        jdbc().execute("TRUNCATE TABLE kjx_outbox_event");
        jdbc().execute("SET FOREIGN_KEY_CHECKS=1");
    }

    protected Map<String, Object> accessPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("category", "ACCESS");
        payload.put("occurredAt", Instant.now().toString());
        payload.put("requestId", "req-log");
        payload.put("method", "GET");
        payload.put("path", "/app/works");
        payload.put("status", 200);
        payload.put("queryKeys", List.of("pageNo"));
        payload.put("latencyMs", 12);
        return payload;
    }

    protected AuditLogWritePort.AuditLogCommand auditCommand() {
        return new AuditLogWritePort.AuditLogCommand(
                UUID.randomUUID().toString(),
                Instant.now(),
                "trace-log",
                "req-log",
                9L,
                "admin",
                List.of("ADMIN"),
                "127.0.0.1",
                "上海",
                "WORK_PUBLISH",
                "work",
                "42",
                Map.of("status", "DRAFT"),
                Map.of("status", "PUBLISHED"),
                "SUCCESS",
                "上架");
    }
}

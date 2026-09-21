package com.koujunxin.notification.outbox.jdbc;

import java.time.Duration;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxProperties;
import com.koujunxin.notification.outbox.OutboxRepository;
import com.koujunxin.notification.outbox.OutboxWriter;
import com.koujunxin.notification.outbox.validation.OutboxPayloadValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Testcontainers(disabledWithoutDocker = true)
abstract class MysqlOutboxSupport {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("kjx_outbox")
            .withUsername("kjx")
            .withPassword("kjx")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static PlatformTransactionManager transactionManager;
    private static TransactionTemplate transactionTemplate;
    private static ObjectMapper objectMapper;
    private static OutboxPayloadValidator validator;

    static synchronized void ensureMigrated() {
        if (dataSource != null) {
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(Duration.ofSeconds(30).toMillis());
        config.setConnectionInitSql("SET time_zone = '+08:00'");
        dataSource = new HikariDataSource(config);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        transactionTemplate = new TransactionTemplate(transactionManager);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        validator = new OutboxPayloadValidator(objectMapper);
    }

    JdbcTemplate jdbc() {
        ensureMigrated();
        return jdbcTemplate;
    }

    TransactionTemplate tx() {
        ensureMigrated();
        return transactionTemplate;
    }

    PlatformTransactionManager txManager() {
        ensureMigrated();
        return transactionManager;
    }

    ObjectMapper mapper() {
        ensureMigrated();
        return objectMapper;
    }

    OutboxWriter writer() {
        ensureMigrated();
        return new JdbcOutboxWriter(jdbcTemplate, objectMapper, validator);
    }

    OutboxRepository repository() {
        ensureMigrated();
        return new JdbcOutboxRepository(jdbcTemplate);
    }

    void truncateOutbox() {
        jdbc().execute("TRUNCATE TABLE kjx_outbox_consume_receipt");
        jdbc().execute("TRUNCATE TABLE kjx_outbox_event");
    }

    OutboxAppendCommand notificationCommand() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("templateCode", "BINDING_APPROVED");
        payload.put("channels", List.of("INBOX"));
        payload.put("accountId", 88);
        payload.put("inboxType", "SYSTEM");
        payload.put("title", "绑定成功");
        payload.put("content", "会员绑定已通过审核");
        return new OutboxAppendCommand(
                UUID.randomUUID().toString(),
                "notification",
                "88",
                "notification.requested",
                "1.0",
                java.time.Instant.now(),
                "trace-1",
                "req-1",
                "idem-1",
                payload);
    }

    JdbcOutboxWorker worker(com.koujunxin.notification.outbox.OutboxPublisher publisher, OutboxProperties properties) {
        return new JdbcOutboxWorker(repository(), publisher, tx(), properties, mapper());
    }
}

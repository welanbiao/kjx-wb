package com.koujunxin.work.jdbc;

import java.time.Duration;

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
import com.koujunxin.work.catalog.ContentVersionRepository;
import com.koujunxin.work.catalog.WorkCatalogService;
import com.koujunxin.work.catalog.WorkRepository;
import com.koujunxin.work.catalog.WorkVersionRepository;
import com.koujunxin.work.port.NoOpWorkAuditPort;
import com.koujunxin.work.port.NoOpWorkLifecycleOutboxPort;
import com.koujunxin.work.query.PublicWorkQueryService;
import com.koujunxin.work.support.InMemoryEntitlementQueryPort;
import com.koujunxin.work.visibility.WorkVisibilityService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Testcontainers(disabledWithoutDocker = true)
abstract class MysqlWorkSupport {

    /**
     * JVM 单例容器。不用 {@code @Container}，避免多个测试类各自 stop 后复用已失效 DataSource。
     */
    static final MySQLContainer<?> MYSQL = createAndStart();

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;
    private static ObjectMapper objectMapper;
    private static WorkRepository workRepository;
    private static WorkVersionRepository workVersionRepository;
    private static ContentVersionRepository contentVersionRepository;
    private static String jdbcUrl;

    private static MySQLContainer<?> createAndStart() {
        MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("kjx_work")
                .withUsername("kjx")
                .withPassword("kjx")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");
        mysql.start();
        Runtime.getRuntime().addShutdownHook(new Thread(mysql::stop));
        return mysql;
    }

    static synchronized void ensureMigrated() {
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
        config.setMaximumPoolSize(12);
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
        PlatformTransactionManager tm = new DataSourceTransactionManager(dataSource);
        transactionTemplate = new TransactionTemplate(tm);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        workRepository = new JdbcWorkRepository(jdbcTemplate, objectMapper);
        workVersionRepository = new JdbcWorkVersionRepository(jdbcTemplate);
        contentVersionRepository = new JdbcContentVersionRepository(jdbcTemplate);
    }

    JdbcTemplate jdbc() {
        ensureMigrated();
        return jdbcTemplate;
    }

    TransactionTemplate tx() {
        ensureMigrated();
        return transactionTemplate;
    }

    WorkCatalogService catalog() {
        ensureMigrated();
        return new WorkCatalogService(
                workRepository,
                workVersionRepository,
                contentVersionRepository,
                transactionTemplate,
                new NoOpWorkAuditPort(),
                new NoOpWorkLifecycleOutboxPort());
    }

    PublicWorkQueryService publicQuery(InMemoryEntitlementQueryPort entitlements) {
        ensureMigrated();
        return new PublicWorkQueryService(workRepository, new WorkVisibilityService(), entitlements);
    }

    void truncateWorks() {
        jdbc().execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc().execute("TRUNCATE TABLE kjx_content_version");
        jdbc().execute("TRUNCATE TABLE kjx_work_version");
        jdbc().execute("TRUNCATE TABLE kjx_work");
        jdbc().execute("SET FOREIGN_KEY_CHECKS = 1");
    }
}

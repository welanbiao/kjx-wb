package com.koujunxin.tool.jdbc;

import java.time.Clock;
import java.time.Duration;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.api.port.SessionAuthorizationPort;
import com.koujunxin.runtime.api.port.SessionStateCommandPort;
import com.koujunxin.runtime.api.port.SessionStateQueryPort;
import com.koujunxin.tool.builtin.BuiltinCatalog;
import com.koujunxin.tool.compose.ComposeEngine;
import com.koujunxin.tool.execute.ToolExecutor;
import com.koujunxin.tool.schema.ToolJsonSchema;
import com.koujunxin.tool.support.ToolHarness;
import com.koujunxin.tool.ui.UiNodeWhitelist;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Testcontainers(disabledWithoutDocker = true)
abstract class MysqlToolSupport {

    static final MySQLContainer<?> MYSQL = start();

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;
    private static ObjectMapper objectMapper;

    private static MySQLContainer<?> start() {
        MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("kjx_tool")
                .withUsername("kjx")
                .withPassword("kjx")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");
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
        objectMapper = new ObjectMapper();
    }

    protected JdbcTemplate jdbc() {
        ensureMigrated();
        return jdbcTemplate;
    }

    protected ObjectMapper mapper() {
        ensureMigrated();
        return objectMapper;
    }

    protected void resetRuntimeTables() {
        jdbc().execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc().execute("TRUNCATE TABLE kjx_tool_call");
        jdbc().execute("TRUNCATE TABLE kjx_tool_rate_bucket");
        jdbc().execute("TRUNCATE TABLE kjx_tool_work_binding");
        jdbc().execute("SET FOREIGN_KEY_CHECKS=1");
    }

    protected ToolExecutor executor(
            EntitlementAccessPort entitlements,
            SessionAuthorizationPort sessions,
            SessionStateQueryPort query,
            SessionStateCommandPort command) {
        ensureMigrated();
        JdbcToolRegistry catalog = new JdbcToolRegistry(jdbcTemplate, objectMapper);
        BuiltinCatalog builtins = new BuiltinCatalog(objectMapper, Clock.systemUTC());
        UiNodeWhitelist ui = JdbcUiNodeTypes.load(jdbcTemplate, objectMapper);
        return new ToolExecutor(
                catalog,
                new JdbcToolCallStore(jdbcTemplate),
                new JdbcRateLimitStore(jdbcTemplate),
                new JdbcToolUnitOfWork(transactionTemplate),
                entitlements,
                sessions,
                query,
                command,
                builtins,
                new ComposeEngine(objectMapper, builtins, ui, Clock.systemUTC()),
                ui,
                new ToolJsonSchema(),
                objectMapper,
                Clock.systemUTC());
    }

    protected ToolHarness.GrantingEntitlement entitlements() {
        return new ToolHarness.GrantingEntitlement();
    }

    protected ToolHarness.OwnerAuth owners() {
        return new ToolHarness.OwnerAuth();
    }

    protected ToolHarness.MemorySessionState sessionState() {
        return new ToolHarness.MemorySessionState(mapper());
    }
}

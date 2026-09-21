package com.koujunxin.ai.jdbc;

import java.time.Duration;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Testcontainers(disabledWithoutDocker = true)
abstract class MysqlAiSupport {

    static final MySQLContainer<?> MYSQL = start();

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;
    private static ObjectMapper objectMapper;

    private static MySQLContainer<?> start() {
        MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("kjx_ai")
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
        config.setConnectionInitSql("SET time_zone = '+08:00'");
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

    protected JdbcPromptCatalog prompts() {
        ensureMigrated();
        return new JdbcPromptCatalog(jdbcTemplate, transactionTemplate);
    }

    protected JdbcConversationStore conversations() {
        ensureMigrated();
        return new JdbcConversationStore(jdbcTemplate, transactionTemplate, objectMapper);
    }

    protected void resetTables() {
        jdbc().execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc().execute("TRUNCATE TABLE kjx_ai_summary");
        jdbc().execute("TRUNCATE TABLE kjx_ai_message");
        jdbc().execute("TRUNCATE TABLE kjx_ai_session");
        jdbc().execute("TRUNCATE TABLE kjx_prompt_template");
        jdbc().execute("SET FOREIGN_KEY_CHECKS=1");
    }
}

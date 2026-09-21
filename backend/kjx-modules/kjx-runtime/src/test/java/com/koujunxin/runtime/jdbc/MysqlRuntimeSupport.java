package com.koujunxin.runtime.jdbc;

import java.time.Duration;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Testcontainers(disabledWithoutDocker = true)
abstract class MysqlRuntimeSupport {

    static final MySQLContainer<?> MYSQL = start();

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;

    private static MySQLContainer<?> start() {
        MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("kjx_runtime")
                .withUsername("kjx")
                .withPassword("kjx")
                .withCommand(
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_0900_ai_ci");
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

    protected TransactionTemplate tx() {
        ensureMigrated();
        return transactionTemplate;
    }

    protected void resetTables() {
        jdbc().execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc().execute("TRUNCATE TABLE kjx_runtime_sandbox_ticket");
        jdbc().execute("TRUNCATE TABLE kjx_runtime_save");
        jdbc().execute("TRUNCATE TABLE kjx_runtime_session");
        jdbc().execute("SET FOREIGN_KEY_CHECKS=1");
    }
}

package com.koujunxin.account.support;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.koujunxin.account.application.AccountApplicationService;
import com.koujunxin.account.application.ClientContext;
import com.koujunxin.account.config.AccountProperties;
import com.koujunxin.account.crypto.PasswordHasher;
import com.koujunxin.account.crypto.PhoneCipher;
import com.koujunxin.account.jdbc.AccountRepository;
import com.koujunxin.account.jdbc.DeviceRepository;
import com.koujunxin.account.jdbc.VerifyCodeAuditRepository;
import com.koujunxin.account.notify.SecurityOutbox;
import com.koujunxin.account.security.InMemoryAppSessionPort;
import com.koujunxin.account.security.RedisLoginLockStore;
import com.koujunxin.account.sms.RedisSmsCodeStore;
import com.koujunxin.account.sms.RedisSmsRateLimiter;
import com.koujunxin.common.idempotent.jdbc.JdbcIdempotencyService;
import com.koujunxin.notification.outbox.jdbc.JdbcOutboxWriter;
import com.koujunxin.notification.outbox.validation.OutboxPayloadValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Testcontainers(disabledWithoutDocker = true)
public abstract class AccountMysqlSupport {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("kjx_account")
            .withUsername("kjx")
            .withPassword("kjx")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static RedissonClient redisson;
    private static ObjectMapper mapper;
    private static PhoneCipher phoneCipher;
    private static AccountApplicationService service;
    private static RecordingSmsDispatchPort sms;
    private static InMemoryAppSessionPort sessions;
    private static AccountRepository accountRepository;

    static synchronized void ensureStarted() {
        if (service != null) {
            return;
        }
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(MYSQL.getJdbcUrl());
        hikari.setUsername(MYSQL.getUsername());
        hikari.setPassword(MYSQL.getPassword());
        hikari.setMaximumPoolSize(12);
        hikari.setConnectionInitSql("SET time_zone = '+08:00'");
        dataSource = new HikariDataSource(hikari);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Config redisConfig = new Config();
        redisConfig.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redisson = Redisson.create(redisConfig);
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        SecureRandom random = new SecureRandom();
        byte[] aes = new byte[32];
        byte[] hmac = new byte[32];
        random.nextBytes(aes);
        random.nextBytes(hmac);
        phoneCipher = new PhoneCipher(aes, hmac);
        AccountProperties properties = AccountProperties.defaults();
        properties.setSmsInterval(Duration.ofMillis(1));
        sms = new RecordingSmsDispatchPort();
        sessions = new InMemoryAppSessionPort(properties);
        accountRepository = new AccountRepository(jdbc);
        service = new AccountApplicationService(
                properties,
                Clock.system(ZoneId.of("Asia/Shanghai")),
                phoneCipher,
                new PasswordHasher(),
                new PassingCaptchaVerificationPort(),
                new RedisSmsCodeStore(redisson),
                new RedisSmsRateLimiter(redisson),
                sms,
                new RedisLoginLockStore(redisson),
                accountRepository,
                new DeviceRepository(jdbc),
                new VerifyCodeAuditRepository(jdbc),
                sessions,
                accountId -> Optional.empty(),
                new SecurityOutbox(new JdbcOutboxWriter(jdbc, mapper, new OutboxPayloadValidator(mapper))),
                new JdbcIdempotencyService(jdbc),
                tx,
                mapper);
    }

    protected AccountApplicationService service() {
        ensureStarted();
        return service;
    }

    protected JdbcTemplate jdbc() {
        ensureStarted();
        return jdbc;
    }

    protected PhoneCipher phones() {
        ensureStarted();
        return phoneCipher;
    }

    protected RecordingSmsDispatchPort sms() {
        ensureStarted();
        return sms;
    }

    protected InMemoryAppSessionPort sessions() {
        ensureStarted();
        return sessions;
    }

    protected AccountRepository accounts() {
        ensureStarted();
        return accountRepository;
    }

    protected ClientContext ctx(String deviceId, String idem) {
        return new ClientContext(deviceId, "127.0.0.1", "web", "JUnit", UUID.randomUUID().toString().replace("-", ""), idem);
    }

    protected String uniquePhone(String suffix11) {
        return suffix11;
    }

    protected static String b64(byte[] raw) {
        return Base64.getEncoder().encodeToString(raw);
    }
}

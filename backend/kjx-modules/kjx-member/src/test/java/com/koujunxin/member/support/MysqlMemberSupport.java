package com.koujunxin.member.support;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

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
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.koujunxin.common.idempotent.jdbc.JdbcIdempotencyService;
import com.koujunxin.member.api.port.ReviewTransactionHook;
import com.koujunxin.member.application.MemberApplicationService;
import com.koujunxin.member.bind.RedisBindAttemptStore;
import com.koujunxin.member.code.MemberCodeGenerator;
import com.koujunxin.member.jdbc.AppealRepository;
import com.koujunxin.member.jdbc.BindingRepository;
import com.koujunxin.member.jdbc.EntitlementRepository;
import com.koujunxin.member.jdbc.HostRepository;
import com.koujunxin.member.jdbc.MemberRepository;
import com.koujunxin.member.jdbc.UnlockRecordRepository;
import com.koujunxin.member.notify.BindingOutbox;
import com.koujunxin.member.port.InMemoryHostDeptPort;
import com.koujunxin.member.port.JdbcBoundMemberQueryPort;
import com.koujunxin.member.recalc.MemberRecalcService;
import com.koujunxin.member.unlock.NewUnlockPolicy;
import com.koujunxin.member.unlock.RedisUnlockFailMerger;
import com.koujunxin.member.unlock.UnlockApplicationService;
import com.koujunxin.member.unlock.UnlockOutbox;
import com.koujunxin.member.api.port.UnlockTransactionHook;
import com.koujunxin.work.visibility.WorkVisibilityService;
import com.koujunxin.notification.outbox.jdbc.JdbcOutboxWriter;
import com.koujunxin.notification.outbox.validation.OutboxPayloadValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Testcontainers(disabledWithoutDocker = true)
public abstract class MysqlMemberSupport {

    static final MySQLContainer<?> MYSQL = createMysql();
    static final GenericContainer<?> REDIS = createRedis();

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static ObjectMapper mapper;
    private static RedissonClient redisson;
    private static JdbcAccountBindingCommandPort accounts;
    private static InMemoryHostDeptPort hostDepts;
    private static RecordingMemberAuditPort audit;
    private static MemberApplicationService service;
    private static UnlockApplicationService unlockService;
    private static EntitlementRepository entitlementRepository;
    private static UnlockRecordRepository unlockRecordRepository;
    private static JdbcTestWorkCatalog works;
    private static JdbcBoundMemberQueryPort snapshots;
    private static UnlockTransactionHook unlockHook = new UnlockTransactionHook() {
    };
    private static ReviewTransactionHook hook = new ReviewTransactionHook() {
    };
    private static String jdbcUrl;

    private static MySQLContainer<?> createMysql() {
        MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("kjx_member")
                .withUsername("kjx")
                .withPassword("kjx")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci")
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(3));
        mysql.start();
        Runtime.getRuntime().addShutdownHook(new Thread(mysql::stop));
        return mysql;
    }

    @SuppressWarnings("resource")
    private static GenericContainer<?> createRedis() {
        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2));
        redis.start();
        Runtime.getRuntime().addShutdownHook(new Thread(redis::stop));
        return redis;
    }

    static synchronized void ensureStarted() {
        if (dataSource != null && MYSQL.getJdbcUrl().equals(jdbcUrl) && !dataSource.isClosed()) {
            return;
        }
        if (dataSource != null) {
            dataSource.close();
        }
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(MYSQL.getJdbcUrl());
        hikari.setUsername(MYSQL.getUsername());
        hikari.setPassword(MYSQL.getPassword());
        hikari.setMaximumPoolSize(64);
        hikari.setConnectionInitSql("SET time_zone = '+08:00'");
        dataSource = new HikariDataSource(hikari);
        jdbcUrl = MYSQL.getJdbcUrl();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        Config redisConfig = new Config();
        redisConfig.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redisson = Redisson.create(redisConfig);
        rebuildService(new ReviewTransactionHook() {
        });
    }

    static synchronized void rebuildService(ReviewTransactionHook nextHook) {
        rebuildService(nextHook, unlockHook == null ? new UnlockTransactionHook() {
        } : unlockHook);
    }

    static synchronized void rebuildService(ReviewTransactionHook nextHook, UnlockTransactionHook nextUnlockHook) {
        hook = nextHook;
        unlockHook = nextUnlockHook;
        HostRepository hosts = new HostRepository(jdbc);
        MemberRepository members = new MemberRepository(jdbc);
        BindingRepository bindings = new BindingRepository(jdbc);
        AppealRepository appeals = new AppealRepository(jdbc, mapper);
        MemberRecalcService recalc = new MemberRecalcService();
        snapshots = new JdbcBoundMemberQueryPort(bindings, members, recalc);
        accounts = new JdbcAccountBindingCommandPort(jdbc);
        hostDepts = new InMemoryHostDeptPort();
        audit = new RecordingMemberAuditPort();
        entitlementRepository = new EntitlementRepository(jdbc);
        unlockRecordRepository = new UnlockRecordRepository(jdbc);
        works = new JdbcTestWorkCatalog(jdbc);
        service = new MemberApplicationService(
                Clock.system(ZoneId.of("Asia/Shanghai")),
                tx,
                mapper,
                recalc,
                new MemberCodeGenerator(),
                hosts,
                members,
                entitlementRepository,
                bindings,
                appeals,
                accounts,
                hostDepts,
                audit,
                new BindingOutbox(new JdbcOutboxWriter(jdbc, mapper, new OutboxPayloadValidator(mapper))),
                new JdbcIdempotencyService(jdbc),
                new RedisBindAttemptStore(redisson, MemberApplicationService.FAIL_LIMIT, Duration.ofMinutes(30)),
                snapshots,
                hook,
                Duration.ofMinutes(30));
        WorkVisibilityService visibility = new WorkVisibilityService();
        unlockService = new UnlockApplicationService(
                Clock.system(ZoneId.of("Asia/Shanghai")),
                tx,
                mapper,
                recalc,
                members,
                entitlementRepository,
                unlockRecordRepository,
                accounts,
                works,
                new NewUnlockPolicy(recalc, visibility, Clock.system(ZoneId.of("Asia/Shanghai"))),
                audit,
                new UnlockOutbox(new JdbcOutboxWriter(jdbc, mapper, new OutboxPayloadValidator(mapper))),
                new JdbcIdempotencyService(jdbc),
                new RedisUnlockFailMerger(redisson, Duration.ofSeconds(60)),
                unlockHook);
    }

    protected MemberApplicationService service() {
        ensureStarted();
        return service;
    }

    protected JdbcTemplate jdbc() {
        ensureStarted();
        return jdbc;
    }

    protected JdbcAccountBindingCommandPort accounts() {
        ensureStarted();
        return accounts;
    }

    protected InMemoryHostDeptPort hostDepts() {
        ensureStarted();
        return hostDepts;
    }

    protected RecordingMemberAuditPort audit() {
        ensureStarted();
        return audit;
    }

    protected JdbcBoundMemberQueryPort snapshots() {
        ensureStarted();
        return snapshots;
    }

    protected UnlockApplicationService unlocks() {
        ensureStarted();
        return unlockService;
    }

    protected JdbcTestWorkCatalog works() {
        ensureStarted();
        return works;
    }

    protected EntitlementRepository entitlements() {
        ensureStarted();
        return entitlementRepository;
    }

    protected UnlockRecordRepository unlockRecords() {
        ensureStarted();
        return unlockRecordRepository;
    }

    protected RedissonClient redisson() {
        ensureStarted();
        return redisson;
    }

    protected void useHook(ReviewTransactionHook next) {
        ensureStarted();
        rebuildService(next, unlockHook);
    }

    protected void useUnlockHook(UnlockTransactionHook next) {
        ensureStarted();
        rebuildService(hook, next);
    }

    protected void truncateAll() {
        jdbc().execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc().execute("TRUNCATE TABLE kjx_binding_appeal");
        jdbc().execute("TRUNCATE TABLE kjx_member_binding");
        jdbc().execute("TRUNCATE TABLE kjx_member");
        jdbc().execute("TRUNCATE TABLE kjx_host");
        jdbc().execute("TRUNCATE TABLE kjx_account");
        jdbc().execute("TRUNCATE TABLE kjx_outbox_event");
        jdbc().execute("TRUNCATE TABLE kjx_idempotency_guard");
        jdbc().execute("TRUNCATE TABLE kjx_member_entitlement");
        jdbc().execute("TRUNCATE TABLE kjx_unlock_record");
        jdbc().execute("TRUNCATE TABLE kjx_content_version");
        jdbc().execute("TRUNCATE TABLE kjx_work_version");
        jdbc().execute("TRUNCATE TABLE kjx_work");
        jdbc().execute("SET FOREIGN_KEY_CHECKS = 1");
        if (redisson != null) {
            redisson.getKeys().flushdb();
        }
        hostDepts().put(0L, "root");
        audit().clear();
        rebuildService(new ReviewTransactionHook() {
        }, new UnlockTransactionHook() {
        });
    }
}

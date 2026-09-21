package com.koujunxin.work.packagecontent;

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
import com.koujunxin.work.asset.AssetService;
import com.koujunxin.work.asset.HmacSignedUrlSigner;
import com.koujunxin.work.asset.InMemoryObjectStorage;
import com.koujunxin.work.asset.RecordingObjectStorage;
import com.koujunxin.work.asset.jdbc.JdbcAssetRepository;
import com.koujunxin.work.asset.jdbc.JdbcOssMetadataRepository;
import com.koujunxin.work.catalog.WorkCatalogService;
import com.koujunxin.work.content.ContentLibraryService;
import com.koujunxin.work.content.jdbc.JdbcContentDocumentRepository;
import com.koujunxin.work.jdbc.JdbcContentVersionRepository;
import com.koujunxin.work.jdbc.JdbcWorkRepository;
import com.koujunxin.work.jdbc.JdbcWorkVersionRepository;
import com.koujunxin.work.music.MusicService;
import com.koujunxin.work.music.jdbc.JdbcMusicRepository;
import com.koujunxin.work.packagecontent.jdbc.JdbcWorkPackageRepository;
import com.koujunxin.work.port.NoOpWorkAuditPort;
import com.koujunxin.work.port.NoOpWorkLifecycleOutboxPort;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Testcontainers(disabledWithoutDocker = true)
abstract class PackageMysqlSupport {

    static final MySQLContainer<?> MYSQL = createAndStart();

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static WorkPackageService packageService;
    private static WorkCatalogService catalogService;
    private static String jdbcUrl;

    private static MySQLContainer<?> createAndStart() {
        MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("kjx_work_pkg")
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
        config.setMaximumPoolSize(8);
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
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        PlatformTransactionManager tm = new DataSourceTransactionManager(dataSource);
        TransactionTemplate tx = new TransactionTemplate(tm);
        JdbcWorkRepository works = new JdbcWorkRepository(jdbcTemplate, mapper);
        JdbcWorkVersionRepository versions = new JdbcWorkVersionRepository(jdbcTemplate);
        JdbcContentVersionRepository contents = new JdbcContentVersionRepository(jdbcTemplate);
        catalogService = new WorkCatalogService(
                works, versions, contents, tx, new NoOpWorkAuditPort(), new NoOpWorkLifecycleOutboxPort());
        WorkPackageLimits limits = WorkPackageLimits.standard();
        RecordingObjectStorage storage = new RecordingObjectStorage(
                new InMemoryObjectStorage(new HmacSignedUrlSigner(HmacSignedUrlSigner.LOCAL_DEV_SECRET)),
                new JdbcOssMetadataRepository(jdbcTemplate));
        packageService = new WorkPackageService(
                catalogService,
                versions,
                new JdbcWorkPackageRepository(jdbcTemplate),
                storage,
                new WorkPackageInspector(mapper, limits),
                new IsolatedZipExtractor(limits),
                new AssetService(new JdbcAssetRepository(jdbcTemplate), storage),
                new ContentLibraryService(new JdbcContentDocumentRepository(jdbcTemplate), catalogService, mapper),
                new MusicService(new JdbcMusicRepository(jdbcTemplate), storage, mapper),
                tx,
                limits,
                mapper);
    }

    JdbcTemplate jdbc() {
        ensureMigrated();
        return jdbcTemplate;
    }

    WorkCatalogService catalog() {
        ensureMigrated();
        return catalogService;
    }

    WorkPackageService packages() {
        ensureMigrated();
        return packageService;
    }

    void truncateAll() {
        jdbc().execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc().execute("TRUNCATE TABLE kjx_work_music_binding");
        jdbc().execute("TRUNCATE TABLE kjx_music_pack_track");
        jdbc().execute("TRUNCATE TABLE kjx_music_pack");
        jdbc().execute("TRUNCATE TABLE kjx_music_track");
        jdbc().execute("TRUNCATE TABLE kjx_asset");
        jdbc().execute("TRUNCATE TABLE kjx_oss_object");
        jdbc().execute("TRUNCATE TABLE kjx_content_document");
        jdbc().execute("TRUNCATE TABLE kjx_work_package");
        jdbc().execute("TRUNCATE TABLE kjx_content_version");
        jdbc().execute("TRUNCATE TABLE kjx_work_version");
        jdbc().execute("TRUNCATE TABLE kjx_work");
        jdbc().execute("SET FOREIGN_KEY_CHECKS = 1");
    }
}

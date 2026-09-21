package com.koujunxin.work.packagecontent;

import java.nio.file.Path;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.work.asset.AssetRepository;
import com.koujunxin.work.asset.AssetService;
import com.koujunxin.work.asset.HmacSignedUrlSigner;
import com.koujunxin.work.asset.InMemoryObjectStorage;
import com.koujunxin.work.asset.LocalFileObjectStorage;
import com.koujunxin.work.asset.ObjectStoragePort;
import com.koujunxin.work.asset.OssMetadataRepository;
import com.koujunxin.work.asset.RecordingObjectStorage;
import com.koujunxin.work.asset.jdbc.JdbcAssetRepository;
import com.koujunxin.work.asset.jdbc.JdbcOssMetadataRepository;
import com.koujunxin.work.catalog.WorkCatalogService;
import com.koujunxin.work.catalog.WorkVersionRepository;
import com.koujunxin.work.content.ContentDocumentRepository;
import com.koujunxin.work.content.ContentLibraryService;
import com.koujunxin.work.content.jdbc.JdbcContentDocumentRepository;
import com.koujunxin.work.music.MusicRepository;
import com.koujunxin.work.music.MusicService;
import com.koujunxin.work.music.jdbc.JdbcMusicRepository;
import com.koujunxin.work.packagecontent.jdbc.JdbcWorkPackageRepository;

@AutoConfiguration(afterName = "com.koujunxin.work.config.WorkAutoConfiguration")
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean({JdbcTemplate.class, WorkCatalogService.class})
public class WorkPackageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    WorkPackageLimits workPackageLimits() {
        return WorkPackageLimits.standard();
    }

    @Bean
    @ConditionalOnMissingBean
    HmacSignedUrlSigner hmacSignedUrlSigner() {
        String secret = System.getenv("KJX_WORK_OSS_HMAC_SECRET");
        return new HmacSignedUrlSigner(secret == null || secret.isBlank()
                ? HmacSignedUrlSigner.LOCAL_DEV_SECRET : secret);
    }

    @Bean
    @ConditionalOnMissingBean(OssMetadataRepository.class)
    OssMetadataRepository ossMetadataRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcOssMetadataRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStoragePort.class)
    ObjectStoragePort objectStoragePort(HmacSignedUrlSigner signer, OssMetadataRepository metadata) {
        String root = System.getenv("KJX_WORK_OSS_ROOT");
        ObjectStoragePort blob = root == null || root.isBlank()
                ? new InMemoryObjectStorage(signer)
                : new LocalFileObjectStorage(Path.of(root), signer);
        return new RecordingObjectStorage(blob, metadata);
    }

    @Bean
    @ConditionalOnMissingBean(WorkPackageRepository.class)
    WorkPackageRepository workPackageRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcWorkPackageRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(AssetRepository.class)
    AssetRepository assetRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcAssetRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ContentDocumentRepository.class)
    ContentDocumentRepository contentDocumentRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcContentDocumentRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(MusicRepository.class)
    MusicRepository musicRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcMusicRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    AssetService assetService(AssetRepository assetRepository, ObjectStoragePort objectStoragePort) {
        return new AssetService(assetRepository, objectStoragePort);
    }

    @Bean
    @ConditionalOnMissingBean
    ContentLibraryService contentLibraryService(
            ContentDocumentRepository contentDocumentRepository,
            WorkCatalogService workCatalogService,
            ObjectMapper objectMapper) {
        return new ContentLibraryService(contentDocumentRepository, workCatalogService, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    MusicService musicService(
            MusicRepository musicRepository, ObjectStoragePort objectStoragePort, ObjectMapper objectMapper) {
        return new MusicService(musicRepository, objectStoragePort, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    WorkPackageInspector workPackageInspector(ObjectMapper objectMapper, WorkPackageLimits workPackageLimits) {
        return new WorkPackageInspector(objectMapper, workPackageLimits);
    }

    @Bean
    @ConditionalOnMissingBean
    IsolatedZipExtractor isolatedZipExtractor(WorkPackageLimits workPackageLimits) {
        return new IsolatedZipExtractor(workPackageLimits);
    }

    @Bean
    @ConditionalOnMissingBean(WorkPackageService.class)
    WorkPackageService workPackageService(
            WorkCatalogService workCatalogService,
            WorkVersionRepository workVersionRepository,
            WorkPackageRepository workPackageRepository,
            ObjectStoragePort objectStoragePort,
            WorkPackageInspector workPackageInspector,
            IsolatedZipExtractor isolatedZipExtractor,
            AssetService assetService,
            ContentLibraryService contentLibraryService,
            MusicService musicService,
            @Qualifier("workTransactionTemplate") TransactionTemplate workTransactionTemplate,
            WorkPackageLimits workPackageLimits,
            ObjectMapper objectMapper) {
        return new WorkPackageService(
                workCatalogService, workVersionRepository, workPackageRepository, objectStoragePort,
                workPackageInspector, isolatedZipExtractor, assetService, contentLibraryService, musicService,
                workTransactionTemplate, workPackageLimits, objectMapper);
    }
}

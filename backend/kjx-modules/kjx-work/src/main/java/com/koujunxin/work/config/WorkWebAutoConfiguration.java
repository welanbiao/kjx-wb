package com.koujunxin.work.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.common.idempotent.IdempotencyService;
import com.koujunxin.work.asset.AssetService;
import com.koujunxin.work.catalog.WorkCatalogService;
import com.koujunxin.work.music.MusicService;
import com.koujunxin.work.music.web.AdminMusicController;
import com.koujunxin.work.asset.web.AdminAssetController;
import com.koujunxin.work.packagecontent.WorkPackageAutoConfiguration;
import com.koujunxin.work.packagecontent.WorkPackageService;
import com.koujunxin.work.packagecontent.web.AdminWorkPackageController;
import com.koujunxin.work.query.PublicWorkQueryService;
import com.koujunxin.work.web.AdminWorkController;
import com.koujunxin.work.web.AppAccountAccessor;
import com.koujunxin.work.web.AppWorkController;

@AutoConfiguration(after = {WorkAutoConfiguration.class, WorkPackageAutoConfiguration.class})
public class WorkWebAutoConfiguration {

    @Bean
    @ConditionalOnBean(PublicWorkQueryService.class)
    @ConditionalOnMissingBean
    AppWorkController appWorkController(PublicWorkQueryService queryService, AppAccountAccessor accounts) {
        return new AppWorkController(queryService, accounts);
    }

    @Bean
    @ConditionalOnBean(WorkCatalogService.class)
    @ConditionalOnMissingBean
    AdminWorkController adminWorkController(
            WorkCatalogService catalog,
            ObjectProvider<IdempotencyService> idempotency,
            ObjectMapper objectMapper) {
        return new AdminWorkController(catalog, idempotency, objectMapper);
    }

    @Bean
    @ConditionalOnBean(WorkPackageService.class)
    @ConditionalOnMissingBean
    AdminWorkPackageController adminWorkPackageController(WorkPackageService packages, ObjectMapper objectMapper) {
        return new AdminWorkPackageController(packages, objectMapper);
    }

    @Bean
    @ConditionalOnBean(AssetService.class)
    @ConditionalOnMissingBean
    AdminAssetController adminAssetController(AssetService assets) {
        return new AdminAssetController(assets);
    }

    @Bean
    @ConditionalOnBean(MusicService.class)
    @ConditionalOnMissingBean
    AdminMusicController adminMusicController(MusicService music) {
        return new AdminMusicController(music);
    }
}

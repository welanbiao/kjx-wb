package com.koujunxin.work.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.common.idempotent.IdempotencyAutoConfiguration;
import com.koujunxin.notification.outbox.OutboxAutoConfiguration;
import com.koujunxin.notification.outbox.OutboxWriter;
import com.koujunxin.work.catalog.ContentVersionRepository;
import com.koujunxin.work.catalog.WorkCatalogService;
import com.koujunxin.work.catalog.WorkRepository;
import com.koujunxin.work.catalog.WorkVersionRepository;
import com.koujunxin.work.jdbc.JdbcContentVersionRepository;
import com.koujunxin.work.jdbc.JdbcWorkRepository;
import com.koujunxin.work.jdbc.JdbcWorkVersionRepository;
import com.koujunxin.work.port.EmptyEntitlementQueryPort;
import com.koujunxin.work.port.EntitlementQueryPort;
import com.koujunxin.work.port.NoOpWorkAuditPort;
import com.koujunxin.work.port.NoOpWorkLifecycleOutboxPort;
import com.koujunxin.work.port.OutboxWorkLifecyclePort;
import com.koujunxin.work.port.WorkAuditPort;
import com.koujunxin.work.port.WorkLifecycleOutboxPort;
import com.koujunxin.work.query.PublicWorkQueryService;
import com.koujunxin.work.visibility.WorkVisibilityService;
import com.koujunxin.work.web.AppAccountAccessor;
import com.koujunxin.work.web.StpKitAppAccountAccessor;

@AutoConfiguration(
        after = {
                JdbcTemplateAutoConfiguration.class,
                IdempotencyAutoConfiguration.class,
                OutboxAutoConfiguration.class
        })
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(JdbcTemplate.class)
public class WorkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WorkRepository.class)
    WorkRepository workRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcWorkRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(WorkVersionRepository.class)
    WorkVersionRepository workVersionRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcWorkVersionRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ContentVersionRepository.class)
    ContentVersionRepository contentVersionRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcContentVersionRepository(jdbcTemplate);
    }

    @Bean
    WorkVisibilityService workVisibilityService() {
        return new WorkVisibilityService();
    }

    @Bean
    @ConditionalOnMissingBean(EntitlementQueryPort.class)
    EntitlementQueryPort entitlementQueryPort() {
        return new EmptyEntitlementQueryPort();
    }

    @Bean
    @ConditionalOnMissingBean(WorkAuditPort.class)
    WorkAuditPort workAuditPort() {
        return new NoOpWorkAuditPort();
    }

    @Bean
    @ConditionalOnMissingBean(WorkLifecycleOutboxPort.class)
    WorkLifecycleOutboxPort workLifecycleOutboxPort(ObjectProvider<OutboxWriter> writers) {
        OutboxWriter writer = writers.getIfAvailable();
        return writer == null ? new NoOpWorkLifecycleOutboxPort() : new OutboxWorkLifecyclePort(writer);
    }

    @Bean
    @ConditionalOnMissingBean(name = "workTransactionTemplate")
    TransactionTemplate workTransactionTemplate(ObjectProvider<PlatformTransactionManager> managers, DataSource dataSource) {
        PlatformTransactionManager manager = managers.getIfAvailable(() -> new DataSourceTransactionManager(dataSource));
        return new TransactionTemplate(manager);
    }

    @Bean
    @ConditionalOnMissingBean(WorkCatalogService.class)
    WorkCatalogService workCatalogService(
            WorkRepository workRepository,
            WorkVersionRepository workVersionRepository,
            ContentVersionRepository contentVersionRepository,
            TransactionTemplate workTransactionTemplate,
            WorkAuditPort workAuditPort,
            WorkLifecycleOutboxPort workLifecycleOutboxPort) {
        return new WorkCatalogService(
                workRepository, workVersionRepository, contentVersionRepository,
                workTransactionTemplate, workAuditPort, workLifecycleOutboxPort);
    }

    @Bean
    @ConditionalOnMissingBean(PublicWorkQueryService.class)
    PublicWorkQueryService publicWorkQueryService(
            WorkRepository workRepository,
            WorkVisibilityService workVisibilityService,
            EntitlementQueryPort entitlementQueryPort) {
        return new PublicWorkQueryService(workRepository, workVisibilityService, entitlementQueryPort);
    }

    @Bean
    @ConditionalOnMissingBean(AppAccountAccessor.class)
    AppAccountAccessor appAccountAccessor() {
        return new StpKitAppAccountAccessor();
    }
}

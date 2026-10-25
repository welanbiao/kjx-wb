package com.koujunxin.server.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.ai.api.port.AiChargePort;
import com.koujunxin.ai.config.AiAutoConfiguration;
import com.koujunxin.billing.api.BillingAuditPort;
import com.koujunxin.billing.api.BillingOutboxPort;
import com.koujunxin.billing.api.PlatformBilling;
import com.koujunxin.billing.config.BillingAutoConfiguration;
import com.koujunxin.billing.ledger.Money;
import com.koujunxin.common.permission.DeptDataScopeGuard;
import com.koujunxin.log.api.port.AuditLogWritePort;
import com.koujunxin.log.api.port.BillingLogWritePort;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.config.LogAutoConfiguration;
import com.koujunxin.log.config.LogMysqlAutoConfiguration;
import com.koujunxin.member.api.port.AccountBindingCommandPort;
import com.koujunxin.member.api.port.DeptAccessPort;
import com.koujunxin.member.api.port.HostDeptPort;
import com.koujunxin.member.api.port.MemberAuditPort;
import com.koujunxin.member.config.MemberAutoConfiguration;
import com.koujunxin.notification.outbox.OutboxAutoConfiguration;
import com.koujunxin.notification.outbox.OutboxWriter;
import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.runtime.api.WorkRuntimePort;
import com.koujunxin.runtime.config.RuntimeAutoConfiguration;
import com.koujunxin.work.asset.AssetService;
import com.koujunxin.work.asset.ObjectStoragePort;
import com.koujunxin.work.config.WorkAutoConfiguration;
import com.koujunxin.work.port.WorkAuditPort;

/**
 * Cross-module adapters live in the composition root so domain modules keep one-way dependencies.
 */
@AutoConfiguration(
        after = {
                JdbcTemplateAutoConfiguration.class,
                LogAutoConfiguration.class,
                LogMysqlAutoConfiguration.class,
                OutboxAutoConfiguration.class
        },
        before = {
                MemberAutoConfiguration.class,
                WorkAutoConfiguration.class,
                BillingAutoConfiguration.class,
                AiAutoConfiguration.class,
                RuntimeAutoConfiguration.class
        })
public class DomainIntegrationAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(AccountBindingCommandPort.class)
    AccountBindingCommandPort accountBindingCommandPort(JdbcTemplate jdbcTemplate) {
        return new JdbcAccountBindingCommandAdapter(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(HostDeptPort.class)
    HostDeptPort hostDeptPort(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<IdentifierGenerator> identifierGenerators,
            @Value("${kjx.member.host-root-dept-id:200}") long rootDeptId) {
        IdentifierGenerator identifierGenerator = identifierGenerators.getIfAvailable();
        if (identifierGenerator == null) {
            throw new IllegalStateException("IdentifierGenerator is required for host department creation");
        }
        return new JdbcHostDeptAdapter(jdbcTemplate, identifierGenerator, rootDeptId);
    }

    @Bean
    @ConditionalOnBean(DeptDataScopeGuard.class)
    @ConditionalOnMissingBean(DeptAccessPort.class)
    DeptAccessPort deptAccessPort(DeptDataScopeGuard guard) {
        return new RuoYiDeptAccessAdapter(guard);
    }

    @Bean
    @ConditionalOnBean({AuditLogWritePort.class, RequestContextPort.class})
    @ConditionalOnMissingBean(MemberAuditPort.class)
    MemberAuditPort memberAuditPort(
            AuditLogWritePort audit,
            RequestContextPort contexts,
            ObjectProvider<com.koujunxin.log.api.port.SecLogWritePort> securityLogs) {
        return new MemberAuditAdapter(audit, contexts, securityLogs);
    }

    @Bean
    @ConditionalOnBean({AuditLogWritePort.class, RequestContextPort.class})
    @ConditionalOnMissingBean(WorkAuditPort.class)
    WorkAuditPort workAuditPort(AuditLogWritePort audit, RequestContextPort contexts) {
        return new WorkAuditAdapter(audit, contexts);
    }

    @Bean
    @ConditionalOnMissingBean(RuntimeAccountPort.class)
    RuntimeAccountPort runtimeAccountPort() {
        return new AppRuntimeAccountAdapter();
    }

    @Bean
    @ConditionalOnBean(OutboxWriter.class)
    @ConditionalOnMissingBean(BillingOutboxPort.class)
    BillingOutboxPort billingOutboxPort(OutboxWriter outbox, ObjectProvider<RequestContextPort> contexts) {
        return new BillingOutboxAdapter(outbox, contexts);
    }

    @Bean
    @ConditionalOnBean(BillingLogWritePort.class)
    @ConditionalOnMissingBean(BillingAuditPort.class)
    BillingAuditPort billingAuditPort(BillingLogWritePort logs, ObjectProvider<RequestContextPort> contexts) {
        return new BillingAuditAdapter(logs, contexts);
    }

    /**
     * 覆盖 {@code NoOpAiChargePort}。PlatformBilling 由后续 {@link BillingAutoConfiguration} 创建，
     * 因此这里用 ObjectProvider 延后解析，而不是 {@code @ConditionalOnBean(PlatformBilling)}。
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(AiChargePort.class)
    AiChargePort aiChargePort(
            ObjectProvider<PlatformBilling> billing,
            JdbcTemplate jdbcTemplate,
            ObjectProvider<RequestContextPort> contexts,
            @Value("${kjx.billing.ai-hold-amount:1.000000}") String holdAmount,
            @Value("${kjx.billing.platform-micros-per-token:1}") long microsPerToken) {
        return new PlatformAiChargeAdapter(
                billing, jdbcTemplate, contexts, Money.of(holdAmount), microsPerToken);
    }

    /**
     * 覆盖 {@code MissingWorkRuntimePort}。AssetService / ObjectStorage 在作品包装配之后才有，调用时再取。
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(WorkRuntimePort.class)
    WorkRuntimePort workRuntimePort(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<AssetService> assets,
            ObjectProvider<ObjectStoragePort> storage,
            ObjectProvider<ObjectMapper> mappers) {
        return new JdbcWorkRuntimeAdapter(
                jdbcTemplate, assets, storage, mappers.getIfAvailable(ObjectMapper::new));
    }
}

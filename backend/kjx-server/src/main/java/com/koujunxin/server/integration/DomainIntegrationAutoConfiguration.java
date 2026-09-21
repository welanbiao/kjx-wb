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
import com.koujunxin.common.permission.DeptDataScopeGuard;
import com.koujunxin.log.api.port.AuditLogWritePort;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.config.LogMysqlAutoConfiguration;
import com.koujunxin.member.api.port.AccountBindingCommandPort;
import com.koujunxin.member.api.port.DeptAccessPort;
import com.koujunxin.member.api.port.HostDeptPort;
import com.koujunxin.member.api.port.MemberAuditPort;
import com.koujunxin.member.config.MemberAutoConfiguration;
import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.work.config.WorkAutoConfiguration;
import com.koujunxin.work.port.WorkAuditPort;

/**
 * Cross-module adapters live in the composition root so domain modules keep one-way dependencies.
 */
@AutoConfiguration(
        after = {JdbcTemplateAutoConfiguration.class, LogMysqlAutoConfiguration.class},
        before = {MemberAutoConfiguration.class, WorkAutoConfiguration.class})
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
}

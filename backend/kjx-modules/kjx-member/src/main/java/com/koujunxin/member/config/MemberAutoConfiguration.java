package com.koujunxin.member.config;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import javax.sql.DataSource;

import org.redisson.api.RedissonClient;
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
import com.koujunxin.account.api.port.BoundMemberQueryPort;
import com.koujunxin.common.idempotent.IdempotencyAutoConfiguration;
import com.koujunxin.common.idempotent.IdempotencyService;
import com.koujunxin.member.api.port.AccountBindingCommandPort;
import com.koujunxin.member.api.port.AppAccountAccessor;
import com.koujunxin.member.api.port.DeptAccessPort;
import com.koujunxin.member.api.port.HostDeptPort;
import com.koujunxin.member.api.port.MemberAuditPort;
import com.koujunxin.member.api.port.ReviewTransactionHook;
import com.koujunxin.member.application.MemberApplicationService;
import com.koujunxin.member.bind.RedisBindAttemptStore;
import com.koujunxin.member.code.MemberCodeGenerator;
import com.koujunxin.member.entitlement.JdbcEntitlementQueryPort;
import com.koujunxin.member.jdbc.AppealRepository;
import com.koujunxin.member.jdbc.BindingRepository;
import com.koujunxin.member.jdbc.EntitlementRepository;
import com.koujunxin.member.jdbc.HostRepository;
import com.koujunxin.member.jdbc.MemberRepository;
import com.koujunxin.member.notify.BindingOutbox;
import com.koujunxin.member.port.InMemoryHostDeptPort;
import com.koujunxin.member.port.JdbcBoundMemberQueryPort;
import com.koujunxin.member.port.LoginHelperDeptAccessPort;
import com.koujunxin.member.port.MissingAccountBindingCommandPort;
import com.koujunxin.member.port.NoOpMemberAuditPort;
import com.koujunxin.member.port.StpKitAppAccountAccessor;
import com.koujunxin.member.recalc.MemberRecalcService;
import com.koujunxin.notification.outbox.OutboxAutoConfiguration;
import com.koujunxin.notification.outbox.OutboxWriter;
import com.koujunxin.work.port.EntitlementQueryPort;

@AutoConfiguration(
        after = {
                JdbcTemplateAutoConfiguration.class,
                IdempotencyAutoConfiguration.class,
                OutboxAutoConfiguration.class
        },
        beforeName = {
                "com.koujunxin.account.config.AccountAutoConfiguration",
                "com.koujunxin.work.config.WorkAutoConfiguration"
        })
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(JdbcTemplate.class)
public class MemberAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MemberRecalcService memberRecalcService() {
        return new MemberRecalcService();
    }

    @Bean
    @ConditionalOnMissingBean
    MemberCodeGenerator memberCodeGenerator() {
        return new MemberCodeGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    HostRepository hostRepository(JdbcTemplate jdbcTemplate) {
        return new HostRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    MemberRepository memberRepository(JdbcTemplate jdbcTemplate) {
        return new MemberRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    EntitlementRepository entitlementRepository(JdbcTemplate jdbcTemplate) {
        return new EntitlementRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(EntitlementQueryPort.class)
    EntitlementQueryPort entitlementQueryPort(EntitlementRepository entitlementRepository) {
        return new JdbcEntitlementQueryPort(entitlementRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    BindingRepository bindingRepository(JdbcTemplate jdbcTemplate) {
        return new BindingRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    AppealRepository appealRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new AppealRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(BoundMemberQueryPort.class)
    BoundMemberQueryPort boundMemberQueryPort(
            BindingRepository bindingRepository,
            MemberRepository memberRepository,
            MemberRecalcService memberRecalcService) {
        return new JdbcBoundMemberQueryPort(bindingRepository, memberRepository, memberRecalcService);
    }

    @Bean
    @ConditionalOnMissingBean(AccountBindingCommandPort.class)
    AccountBindingCommandPort accountBindingCommandPort() {
        return new MissingAccountBindingCommandPort();
    }

    @Bean
    @ConditionalOnMissingBean(HostDeptPort.class)
    HostDeptPort hostDeptPort() {
        return new InMemoryHostDeptPort();
    }

    @Bean
    @ConditionalOnMissingBean(MemberAuditPort.class)
    MemberAuditPort memberAuditPort() {
        return new NoOpMemberAuditPort();
    }

    @Bean
    @ConditionalOnMissingBean(DeptAccessPort.class)
    DeptAccessPort deptAccessPort() {
        return new LoginHelperDeptAccessPort();
    }

    @Bean
    @ConditionalOnMissingBean(AppAccountAccessor.class)
    AppAccountAccessor memberAppAccountAccessor() {
        return new StpKitAppAccountAccessor();
    }

    @Bean
    @ConditionalOnMissingBean(ReviewTransactionHook.class)
    ReviewTransactionHook reviewTransactionHook() {
        return new ReviewTransactionHook() {
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedissonClient.class)
    RedisBindAttemptStore redisBindAttemptStore(RedissonClient redissonClient) {
        return new RedisBindAttemptStore(redissonClient, MemberApplicationService.FAIL_LIMIT, Duration.ofMinutes(30));
    }

    @Bean
    @ConditionalOnMissingBean(name = "memberTransactionTemplate")
    TransactionTemplate memberTransactionTemplate(
            ObjectProvider<PlatformTransactionManager> managers, DataSource dataSource) {
        PlatformTransactionManager manager = managers.getIfAvailable(() -> new DataSourceTransactionManager(dataSource));
        return new TransactionTemplate(manager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({OutboxWriter.class, IdempotencyService.class})
    BindingOutbox bindingOutbox(OutboxWriter outboxWriter) {
        return new BindingOutbox(outboxWriter);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({OutboxWriter.class, IdempotencyService.class, BindingOutbox.class})
    MemberApplicationService memberApplicationService(
            TransactionTemplate memberTransactionTemplate,
            ObjectMapper objectMapper,
            MemberRecalcService memberRecalcService,
            MemberCodeGenerator memberCodeGenerator,
            HostRepository hostRepository,
            MemberRepository memberRepository,
            EntitlementRepository entitlementRepository,
            BindingRepository bindingRepository,
            AppealRepository appealRepository,
            AccountBindingCommandPort accountBindingCommandPort,
            HostDeptPort hostDeptPort,
            MemberAuditPort memberAuditPort,
            BindingOutbox bindingOutbox,
            IdempotencyService idempotencyService,
            ObjectProvider<RedisBindAttemptStore> attempts,
            BoundMemberQueryPort boundMemberQueryPort,
            ReviewTransactionHook reviewTransactionHook) {
        JdbcBoundMemberQueryPort snapshots = boundMemberQueryPort instanceof JdbcBoundMemberQueryPort jdbc
                ? jdbc
                : new JdbcBoundMemberQueryPort(bindingRepository, memberRepository, memberRecalcService);
        return new MemberApplicationService(
                Clock.system(ZoneId.of("Asia/Shanghai")),
                memberTransactionTemplate,
                objectMapper,
                memberRecalcService,
                memberCodeGenerator,
                hostRepository,
                memberRepository,
                entitlementRepository,
                bindingRepository,
                appealRepository,
                accountBindingCommandPort,
                hostDeptPort,
                memberAuditPort,
                bindingOutbox,
                idempotencyService,
                attempts.getIfAvailable(),
                snapshots,
                reviewTransactionHook,
                Duration.ofMinutes(30));
    }
}

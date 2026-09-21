package com.koujunxin.member.config;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.common.idempotent.IdempotencyService;
import com.koujunxin.member.api.port.AccountBindingCommandPort;
import com.koujunxin.member.api.port.MemberAuditPort;
import com.koujunxin.member.api.port.UnlockTransactionHook;
import com.koujunxin.member.entitlement.JdbcEntitlementAccessPort;
import com.koujunxin.member.jdbc.EntitlementRepository;
import com.koujunxin.member.jdbc.MemberRepository;
import com.koujunxin.member.jdbc.UnlockRecordRepository;
import com.koujunxin.member.recalc.MemberRecalcService;
import com.koujunxin.member.unlock.NewUnlockPolicy;
import com.koujunxin.member.unlock.RedisUnlockFailMerger;
import com.koujunxin.member.unlock.UnlockApplicationService;
import com.koujunxin.member.unlock.UnlockOutbox;
import com.koujunxin.notification.outbox.OutboxWriter;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.work.api.WorkCatalogQueryPort;
import com.koujunxin.work.visibility.WorkVisibilityService;

/**
 * 必须晚于 WP-12 {@code WorkAutoConfiguration}，才能注入 {@code WorkCatalogQueryPort} / {@code WorkVisibilityService}。
 * {@code EntitlementQueryPort} 仍由 {@link MemberAutoConfiguration} 在 Work 之前注册，覆盖 Empty 桩。
 */
@AutoConfiguration(
        after = MemberAutoConfiguration.class,
        afterName = "com.koujunxin.work.config.WorkAutoConfiguration")
@ConditionalOnClass({JdbcTemplate.class, WorkCatalogQueryPort.class, WorkVisibilityService.class})
@ConditionalOnBean({JdbcTemplate.class, WorkCatalogQueryPort.class, WorkVisibilityService.class})
public class MemberUnlockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    UnlockRecordRepository unlockRecordRepository(JdbcTemplate jdbcTemplate) {
        return new UnlockRecordRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    NewUnlockPolicy newUnlockPolicy(MemberRecalcService recalc, WorkVisibilityService visibility) {
        return new NewUnlockPolicy(recalc, visibility, Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    @Bean
    @ConditionalOnMissingBean(UnlockTransactionHook.class)
    UnlockTransactionHook unlockTransactionHook() {
        return new UnlockTransactionHook() {
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedissonClient.class)
    RedisUnlockFailMerger redisUnlockFailMerger(RedissonClient redissonClient) {
        return new RedisUnlockFailMerger(redissonClient, Duration.ofSeconds(60));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(OutboxWriter.class)
    UnlockOutbox unlockOutbox(OutboxWriter outboxWriter) {
        return new UnlockOutbox(outboxWriter);
    }

    @Bean
    @ConditionalOnMissingBean(EntitlementAccessPort.class)
    EntitlementAccessPort entitlementAccessPort(
            EntitlementRepository entitlementRepository,
            WorkCatalogQueryPort workCatalogQueryPort,
            MemberAuditPort memberAuditPort) {
        return new JdbcEntitlementAccessPort(entitlementRepository, workCatalogQueryPort, memberAuditPort);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({OutboxWriter.class, IdempotencyService.class, UnlockOutbox.class})
    UnlockApplicationService unlockApplicationService(
            TransactionTemplate memberTransactionTemplate,
            ObjectMapper objectMapper,
            MemberRecalcService memberRecalcService,
            MemberRepository memberRepository,
            EntitlementRepository entitlementRepository,
            UnlockRecordRepository unlockRecordRepository,
            AccountBindingCommandPort accountBindingCommandPort,
            WorkCatalogQueryPort workCatalogQueryPort,
            NewUnlockPolicy newUnlockPolicy,
            MemberAuditPort memberAuditPort,
            UnlockOutbox unlockOutbox,
            IdempotencyService idempotencyService,
            ObjectProvider<RedisUnlockFailMerger> failMerger,
            UnlockTransactionHook unlockTransactionHook) {
        return new UnlockApplicationService(
                Clock.system(ZoneId.of("Asia/Shanghai")),
                memberTransactionTemplate,
                objectMapper,
                memberRecalcService,
                memberRepository,
                entitlementRepository,
                unlockRecordRepository,
                accountBindingCommandPort,
                workCatalogQueryPort,
                newUnlockPolicy,
                memberAuditPort,
                unlockOutbox,
                idempotencyService,
                failMerger.getIfAvailable(),
                unlockTransactionHook);
    }
}

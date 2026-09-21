package com.koujunxin.billing.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.koujunxin.billing.api.BillingAuditPort;
import com.koujunxin.billing.api.BillingOutboxPort;
import com.koujunxin.billing.api.BillingProjection;
import com.koujunxin.billing.jdbc.JdbcBillingService;

/**
 * 无 DataSource、事务管理器或 Outbox 端口时不创建账本服务，避免空库启动失败。
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass(JdbcTemplate.class)
public class BillingAutoConfiguration {

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, PlatformTransactionManager.class, BillingOutboxPort.class})
    @ConditionalOnMissingBean
    JdbcBillingService jdbcBillingService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            BillingOutboxPort outbox,
            org.springframework.beans.factory.ObjectProvider<BillingAuditPort> audit,
            org.springframework.beans.factory.ObjectProvider<BillingProjection> projection) {
        BillingAuditPort auditPort = audit.getIfAvailable(() -> (memberId, bizType, direction, amount, idempotencyKey, traceId, requestId) -> {
        });
        return new JdbcBillingService(
                jdbcTemplate,
                new TransactionTemplate(transactionManager),
                outbox,
                auditPort,
                projection.getIfAvailable(BillingProjection::noop));
    }
}

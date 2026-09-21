package com.koujunxin.log.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.log.api.port.AnalyticsProjectionPort;
import com.koujunxin.log.api.port.ObservabilityMetricsPort;
import com.koujunxin.log.api.port.ProjectionReconcilePort;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.projection.ClickHouseHttpClient;
import com.koujunxin.log.projection.ClickHouseProjectionPublisher;
import com.koujunxin.log.projection.ClickHouseProjectionSink;
import com.koujunxin.log.projection.JdbcProjectionReconciler;
import com.koujunxin.log.projection.OutboxAnalyticsProjectionPort;
import com.koujunxin.log.redact.LogRedactor;
import com.koujunxin.notification.outbox.OutboxAutoConfiguration;
import com.koujunxin.notification.outbox.OutboxPublisher;
import com.koujunxin.notification.outbox.OutboxWriter;
import com.koujunxin.notification.outbox.consume.ConsumerIdempotencyPort;

/**
 * Outbox → ClickHouse 投影。ClickHouse 默认关闭；无 Publisher 时才注册缺省分析 Publisher。
 */
@AutoConfiguration(after = {
        LogAutoConfiguration.class,
        LogMysqlAutoConfiguration.class,
        OutboxAutoConfiguration.class
})
public class LogProjectionAutoConfiguration {

    @Bean
    @ConditionalOnBean(OutboxWriter.class)
    @ConditionalOnMissingBean(AnalyticsProjectionPort.class)
    AnalyticsProjectionPort analyticsProjectionPort(
            OutboxWriter outboxWriter,
            RequestContextPort requestContextPort,
            LogRedactor redactor) {
        return new OutboxAnalyticsProjectionPort(outboxWriter, requestContextPort, redactor);
    }

    @Bean
    @ConditionalOnProperty(prefix = "kjx.log.clickhouse", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    ClickHouseHttpClient clickHouseHttpClient(LogProperties properties) {
        LogProperties.ClickHouse ch = properties.getClickhouse();
        int timeout = ch.getTimeoutMs() <= 0 ? 2000 : ch.getTimeoutMs();
        return new ClickHouseHttpClient(ch.getUrl(), ch.getUsername(), ch.getPassword(), Duration.ofMillis(timeout));
    }

    @Bean
    @ConditionalOnBean({ClickHouseHttpClient.class, ObjectMapper.class})
    @ConditionalOnMissingBean
    ClickHouseProjectionSink clickHouseProjectionSink(ClickHouseHttpClient client, ObjectMapper objectMapper) {
        return new ClickHouseProjectionSink(client, objectMapper);
    }

    @Bean
    @ConditionalOnBean({ClickHouseProjectionSink.class, ConsumerIdempotencyPort.class})
    @ConditionalOnMissingBean(OutboxPublisher.class)
    OutboxPublisher clickHouseProjectionPublisher(
            ConsumerIdempotencyPort idempotency,
            ClickHouseProjectionSink sink,
            ObservabilityMetricsPort metrics,
            LogRedactor redactor) {
        return new ClickHouseProjectionPublisher(idempotency, sink, metrics, redactor);
    }

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, ClickHouseProjectionSink.class})
    @ConditionalOnMissingBean(ProjectionReconcilePort.class)
    ProjectionReconcilePort projectionReconcilePort(
            JdbcTemplate jdbcTemplate,
            ClickHouseProjectionSink sink,
            ObservabilityMetricsPort metrics,
            LogRedactor redactor) {
        return new JdbcProjectionReconciler(jdbcTemplate, sink, metrics, redactor);
    }
}

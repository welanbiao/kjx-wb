package com.koujunxin.log.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.log.api.port.AuditLogWritePort;
import com.koujunxin.log.api.port.BillingLogWritePort;
import com.koujunxin.log.api.port.BizLogWritePort;
import com.koujunxin.log.api.port.ObservabilityMetricsPort;
import com.koujunxin.log.api.port.SecLogWritePort;
import com.koujunxin.log.mysql.JdbcAuditLogWriter;
import com.koujunxin.log.mysql.JdbcBillingLogWriter;
import com.koujunxin.log.mysql.JdbcBizLogWriter;
import com.koujunxin.log.mysql.JdbcSecLogWriter;
import com.koujunxin.log.mysql.OutboxBacklogMetricsBinder;
import com.koujunxin.log.mysql.OutboxBacklogProbe;
import com.koujunxin.log.redact.LogRedactor;

/**
 * MySQL AUDIT/BIZ/BILLING/SEC 写入。无 JdbcTemplate 时整段跳过，不令 test profile 失败。
 */
@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, LogAutoConfiguration.class})
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(JdbcTemplate.class)
public class LogMysqlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditLogWritePort.class)
    @ConditionalOnBean(ObjectMapper.class)
    AuditLogWritePort auditLogWritePort(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, LogRedactor redactor) {
        return new JdbcAuditLogWriter(jdbcTemplate, objectMapper, redactor);
    }

    @Bean
    @ConditionalOnMissingBean(BizLogWritePort.class)
    @ConditionalOnBean(ObjectMapper.class)
    BizLogWritePort bizLogWritePort(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, LogRedactor redactor) {
        return new JdbcBizLogWriter(jdbcTemplate, objectMapper, redactor);
    }

    @Bean
    @ConditionalOnMissingBean(BillingLogWritePort.class)
    @ConditionalOnBean(ObjectMapper.class)
    BillingLogWritePort billingLogWritePort(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, LogRedactor redactor) {
        return new JdbcBillingLogWriter(jdbcTemplate, objectMapper, redactor);
    }

    @Bean
    @ConditionalOnMissingBean(SecLogWritePort.class)
    @ConditionalOnBean(ObjectMapper.class)
    SecLogWritePort secLogWritePort(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, LogRedactor redactor) {
        return new JdbcSecLogWriter(jdbcTemplate, objectMapper, redactor);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxBacklogProbe outboxBacklogProbe(JdbcTemplate jdbcTemplate, ObservabilityMetricsPort metrics) {
        return new OutboxBacklogProbe(jdbcTemplate, metrics);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class OutboxGaugeConfiguration {

        @Bean
        @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
        @ConditionalOnMissingBean
        OutboxBacklogMetricsBinder outboxBacklogMetricsBinder(JdbcTemplate jdbcTemplate) {
            return new OutboxBacklogMetricsBinder(jdbcTemplate);
        }
    }
}

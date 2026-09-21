package com.koujunxin.log.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import com.koujunxin.log.api.port.AnalyticsProjectionPort;
import com.koujunxin.log.api.port.AuditLogWritePort;
import com.koujunxin.log.api.port.BillingLogWritePort;
import com.koujunxin.log.api.port.ObservabilityMetricsPort;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.api.port.TracePropagationPort;
import com.koujunxin.log.redact.LogRedactor;
import com.koujunxin.notification.outbox.OutboxPublisher;

class LogAutoConfigurationDegradeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    LogAutoConfiguration.class,
                    LogMysqlAutoConfiguration.class,
                    LogProjectionAutoConfiguration.class));

    @Test
    void startsWithoutDataSourceOrPublisher() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LogRedactor.class);
            assertThat(context).hasSingleBean(RequestContextPort.class);
            assertThat(context).hasSingleBean(TracePropagationPort.class);
            assertThat(context).hasSingleBean(ObservabilityMetricsPort.class);
            assertThat(context).doesNotHaveBean(AuditLogWritePort.class);
            assertThat(context).doesNotHaveBean(BillingLogWritePort.class);
            assertThat(context).doesNotHaveBean(AnalyticsProjectionPort.class);
            assertThat(context).doesNotHaveBean(OutboxPublisher.class);
        });
    }

    @Test
    void clickhouseDisabledDoesNotCreatePublisher() {
        runner.withPropertyValues("kjx.log.clickhouse.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OutboxPublisher.class);
        });
    }

    @Test
    void webApplicationRegistersMdcFilter() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, LogAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.containsBean("mdcRequestContextFilter")).isTrue();
                });
    }
}

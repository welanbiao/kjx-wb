package com.koujunxin.log.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.koujunxin.log.api.port.ObservabilityMetricsPort;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.log.api.port.TracePropagationPort;
import com.koujunxin.log.context.HeaderTracePropagation;
import com.koujunxin.log.context.MdcRequestContextFilter;
import com.koujunxin.log.context.MdcRequestContextPort;
import com.koujunxin.log.metrics.MicrometerObservabilityMetrics;
import com.koujunxin.log.metrics.NoOpObservabilityMetrics;
import com.koujunxin.log.redact.LogRedactor;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.Filter;

/**
 * 核心自动配置：脱敏、MDC、传播端口。无 DataSource / Publisher 时仍可启动。
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
public class LogAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "kjx.log")
    @ConditionalOnMissingBean
    LogProperties logProperties() {
        return LogProperties.defaults();
    }

    @Bean
    @ConditionalOnMissingBean
    LogRedactor logRedactor() {
        return new LogRedactor();
    }

    @Bean
    @ConditionalOnMissingBean(RequestContextPort.class)
    RequestContextPort requestContextPort() {
        return new MdcRequestContextPort();
    }

    @Bean
    @ConditionalOnMissingBean(TracePropagationPort.class)
    TracePropagationPort tracePropagationPort(RequestContextPort requestContextPort) {
        return new HeaderTracePropagation(requestContextPort);
    }

    @Bean
    @ConditionalOnMissingBean(ObservabilityMetricsPort.class)
    @ConditionalOnMissingClass("io.micrometer.core.instrument.MeterRegistry")
    ObservabilityMetricsPort noOpObservabilityMetrics() {
        return new NoOpObservabilityMetrics();
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnClass(Filter.class)
    @ConditionalOnMissingBean(name = "mdcRequestContextFilter")
    FilterRegistrationBean<MdcRequestContextFilter> mdcRequestContextFilter(
            RequestContextPort requestContextPort,
            TracePropagationPort tracePropagationPort) {
        FilterRegistrationBean<MdcRequestContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MdcRequestContextFilter(requestContextPort, tracePropagationPort));
        registration.setName("mdcRequestContextFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MicrometerMetricsConfiguration {

        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        @ConditionalOnMissingBean(ObservabilityMetricsPort.class)
        ObservabilityMetricsPort micrometerObservabilityMetrics(MeterRegistry meterRegistry) {
            return new MicrometerObservabilityMetrics(meterRegistry);
        }

        @Bean
        @ConditionalOnMissingBean({ObservabilityMetricsPort.class, MeterRegistry.class})
        ObservabilityMetricsPort noOpWhenMeterRegistryAbsent() {
            return new NoOpObservabilityMetrics();
        }
    }
}

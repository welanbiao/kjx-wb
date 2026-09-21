package com.koujunxin.notification.outbox;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.notification.outbox.consume.ConsumerIdempotencyPort;
import com.koujunxin.notification.outbox.jdbc.JdbcConsumerIdempotencyStore;
import com.koujunxin.notification.outbox.jdbc.JdbcOutboxRepository;
import com.koujunxin.notification.outbox.jdbc.JdbcOutboxWorker;
import com.koujunxin.notification.outbox.jdbc.JdbcOutboxWriter;
import com.koujunxin.notification.outbox.validation.OutboxPayloadValidator;
import org.springframework.beans.factory.ObjectProvider;

@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, JacksonAutoConfiguration.class})
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(JdbcTemplate.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ObjectMapper.class)
    OutboxPayloadValidator outboxPayloadValidator(ObjectMapper objectMapper) {
        return new OutboxPayloadValidator(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxWriter.class)
    @ConditionalOnBean({ObjectMapper.class, OutboxPayloadValidator.class})
    OutboxWriter outboxWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, OutboxPayloadValidator validator) {
        return new JdbcOutboxWriter(jdbcTemplate, objectMapper, validator);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxRepository.class)
    OutboxRepository outboxRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcOutboxRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ConsumerIdempotencyPort.class)
    ConsumerIdempotencyPort consumerIdempotencyPort(JdbcTemplate jdbcTemplate) {
        return new JdbcConsumerIdempotencyStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxProperties outboxProperties() {
        return OutboxProperties.defaults();
    }

    @Bean
    @ConditionalOnBean({OutboxPublisher.class, ObjectMapper.class})
    @ConditionalOnMissingBean(OutboxWorker.class)
    OutboxWorker outboxWorker(
            OutboxRepository repository,
            OutboxPublisher publisher,
            DataSource dataSource,
            OutboxProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<PlatformTransactionManager> txManagers) {
        PlatformTransactionManager txManager = txManagers.getIfAvailable(() -> new DataSourceTransactionManager(dataSource));
        return new JdbcOutboxWorker(repository, publisher, new TransactionTemplate(txManager), properties, objectMapper);
    }
}

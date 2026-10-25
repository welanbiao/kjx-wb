package com.koujunxin.tool.config;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.api.port.SessionAuthorizationPort;
import com.koujunxin.runtime.api.port.SessionStateCommandPort;
import com.koujunxin.runtime.api.port.SessionStateQueryPort;
import com.koujunxin.runtime.api.port.ToolExecutionPort;
import com.koujunxin.tool.builtin.BuiltinCatalog;
import com.koujunxin.tool.compose.ComposeEngine;
import com.koujunxin.tool.execute.RateLimitStore;
import com.koujunxin.tool.execute.ToolCallStore;
import com.koujunxin.tool.execute.ToolExecutor;
import com.koujunxin.tool.execute.ToolUnitOfWork;
import com.koujunxin.tool.jdbc.JdbcRateLimitStore;
import com.koujunxin.tool.jdbc.JdbcToolCallStore;
import com.koujunxin.tool.jdbc.JdbcToolRegistry;
import com.koujunxin.tool.jdbc.JdbcToolUnitOfWork;
import com.koujunxin.tool.jdbc.JdbcUiNodeTypes;
import com.koujunxin.tool.registry.ToolCatalog;
import com.koujunxin.tool.schema.ToolJsonSchema;
import com.koujunxin.tool.ui.UiNodeWhitelist;

/**
 * 缺 DataSource、事务或 runtime-api 端口时不创建执行器，避免空库启动失败。
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass(JdbcTemplate.class)
public class ToolAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(ToolCatalog.class)
    JdbcToolRegistry jdbcToolRegistry(JdbcTemplate jdbcTemplate, ObjectProvider<ObjectMapper> objectMapper) {
        return new JdbcToolRegistry(jdbcTemplate, mapper(objectMapper));
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(ToolCallStore.class)
    JdbcToolCallStore jdbcToolCallStore(JdbcTemplate jdbcTemplate) {
        return new JdbcToolCallStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(RateLimitStore.class)
    JdbcRateLimitStore jdbcRateLimitStore(JdbcTemplate jdbcTemplate) {
        return new JdbcRateLimitStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, PlatformTransactionManager.class})
    @ConditionalOnMissingBean(ToolUnitOfWork.class)
    JdbcToolUnitOfWork jdbcToolUnitOfWork(PlatformTransactionManager transactionManager) {
        return new JdbcToolUnitOfWork(new TransactionTemplate(transactionManager));
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(UiNodeWhitelist.class)
    UiNodeWhitelist uiNodeWhitelist(JdbcTemplate jdbcTemplate, ObjectProvider<ObjectMapper> objectMapper) {
        return JdbcUiNodeTypes.load(jdbcTemplate, mapper(objectMapper));
    }

    @Bean
    @ConditionalOnMissingBean(BuiltinCatalog.class)
    BuiltinCatalog builtinCatalog(ObjectProvider<ObjectMapper> objectMapper) {
        return new BuiltinCatalog(mapper(objectMapper), Clock.systemUTC());
    }

    @Bean
    @ConditionalOnBean(UiNodeWhitelist.class)
    @ConditionalOnMissingBean(ComposeEngine.class)
    ComposeEngine composeEngine(ObjectProvider<ObjectMapper> objectMapper, BuiltinCatalog builtins, UiNodeWhitelist ui) {
        return new ComposeEngine(mapper(objectMapper), builtins, ui, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnBean({
            ToolCatalog.class,
            ToolCallStore.class,
            RateLimitStore.class,
            ToolUnitOfWork.class,
            EntitlementAccessPort.class,
            SessionAuthorizationPort.class,
            SessionStateQueryPort.class,
            SessionStateCommandPort.class,
            UiNodeWhitelist.class
    })
    @ConditionalOnMissingBean(ToolExecutionPort.class)
    ToolExecutor toolExecutor(
            ToolCatalog catalog,
            ToolCallStore calls,
            RateLimitStore rates,
            ToolUnitOfWork unitOfWork,
            EntitlementAccessPort entitlements,
            SessionAuthorizationPort sessionAuth,
            SessionStateQueryPort stateQuery,
            SessionStateCommandPort stateCommand,
            BuiltinCatalog builtins,
            ComposeEngine compose,
            UiNodeWhitelist ui,
            ObjectProvider<ObjectMapper> objectMapper) {
        return new ToolExecutor(
                catalog,
                calls,
                rates,
                unitOfWork,
                entitlements,
                sessionAuth,
                stateQuery,
                stateCommand,
                builtins,
                compose,
                ui,
                new ToolJsonSchema(),
                mapper(objectMapper),
                Clock.systemUTC());
    }

    private static ObjectMapper mapper(ObjectProvider<ObjectMapper> objectMapper) {
        ObjectMapper mapper = objectMapper.getIfAvailable(ObjectMapper::new).copy();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}

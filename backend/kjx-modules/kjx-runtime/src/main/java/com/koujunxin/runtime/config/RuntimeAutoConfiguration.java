package com.koujunxin.runtime.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.koujunxin.runtime.api.MissingWorkRuntimePort;
import com.koujunxin.runtime.api.RuntimeAiPort;
import com.koujunxin.runtime.api.WorkRuntimePort;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.api.port.SessionAuthorizationPort;
import com.koujunxin.runtime.api.port.SessionStateCommandPort;
import com.koujunxin.runtime.api.port.SessionStateQueryPort;
import com.koujunxin.runtime.api.port.ToolExecutionPort;
import com.koujunxin.runtime.ledger.SaveSigner;
import com.koujunxin.runtime.sandbox.SandboxGuard;
import com.koujunxin.runtime.session.JdbcRuntimeStore;
import com.koujunxin.runtime.session.RuntimeApplicationService;
import com.koujunxin.runtime.session.RuntimeStore;
import com.koujunxin.runtime.session.SessionStateService;
import com.koujunxin.runtime.api.dto.ToolResult;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass(JdbcTemplate.class)
public class RuntimeAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(RuntimeStore.class)
    RuntimeStore runtimeStore(JdbcTemplate jdbcTemplate) {
        return new JdbcRuntimeStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(WorkRuntimePort.class)
    WorkRuntimePort missingWorkRuntimePort() {
        return new MissingWorkRuntimePort();
    }

    @Bean
    @ConditionalOnBean({RuntimeStore.class, WorkRuntimePort.class})
    @ConditionalOnMissingBean(SessionStateService.class)
    SessionStateService sessionStateService(RuntimeStore store, WorkRuntimePort works) {
        return new SessionStateService(store, works);
    }

    @Bean
    @ConditionalOnBean(SessionStateService.class)
    @ConditionalOnMissingBean(SessionStateQueryPort.class)
    SessionStateQueryPort sessionStateQueryPort(SessionStateService sessions) {
        return sessions;
    }

    @Bean
    @ConditionalOnBean(SessionStateService.class)
    @ConditionalOnMissingBean(SessionStateCommandPort.class)
    SessionStateCommandPort sessionStateCommandPort(SessionStateService sessions) {
        return sessions;
    }

    @Bean
    @ConditionalOnBean(SessionStateService.class)
    @ConditionalOnMissingBean(SessionAuthorizationPort.class)
    SessionAuthorizationPort sessionAuthorizationPort(SessionStateService sessions) {
        return sessions;
    }

    @Bean
    @ConditionalOnMissingBean(SaveSigner.class)
    SaveSigner saveSigner(Environment environment) {
        return new SaveSigner(environment.getProperty("kjx.runtime.save-hmac-secret", "local-dev-not-a-runtime-key"));
    }

    @Bean
    @ConditionalOnBean(RuntimeStore.class)
    @ConditionalOnMissingBean(SandboxGuard.class)
    SandboxGuard sandboxGuard(RuntimeStore store, Environment environment) {
        Duration ttl = Duration.parse(environment.getProperty("kjx.runtime.sandbox-ttl", "PT2H"));
        return new SandboxGuard(store, Clock.systemUTC(), ttl);
    }

    @Bean
    @ConditionalOnBean({
        RuntimeStore.class,
        SessionStateService.class,
        EntitlementAccessPort.class,
        WorkRuntimePort.class,
        SaveSigner.class,
        SandboxGuard.class
    })
    @ConditionalOnMissingBean(RuntimeApplicationService.class)
    RuntimeApplicationService runtimeApplicationService(
            RuntimeStore store,
            SessionStateService sessions,
            EntitlementAccessPort entitlements,
            WorkRuntimePort works,
            SaveSigner signer,
            SandboxGuard sandbox,
            org.springframework.beans.factory.ObjectProvider<ToolExecutionPort> tools,
            org.springframework.beans.factory.ObjectProvider<RuntimeAiPort> ai,
            org.springframework.beans.factory.ObjectProvider<PlatformTransactionManager> txManager) {
        ToolExecutionPort toolPort = tools.getIfAvailable(() -> command ->
                ToolResult.fail("TOOL_UNAVAILABLE", "ToolExecutionPort not wired"));
        PlatformTransactionManager manager = txManager.getIfAvailable();
        TransactionTemplate tx = manager == null ? null : new TransactionTemplate(manager);
        return new RuntimeApplicationService(
                store, sessions, entitlements, works, signer, sandbox, toolPort, ai.getIfAvailable(), tx);
    }
}

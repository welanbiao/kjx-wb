package com.koujunxin.ai.config;

import java.time.Clock;
import java.time.ZoneId;

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
import com.koujunxin.ai.api.port.AiChargePort;
import com.koujunxin.ai.api.port.ConversationStore;
import com.koujunxin.ai.api.port.LangfusePort;
import com.koujunxin.ai.api.port.ModelRouter;
import com.koujunxin.ai.api.port.PromptCatalog;
import com.koujunxin.ai.api.port.UpstreamChatClient;
import com.koujunxin.ai.chat.ChatCompletionService;
import com.koujunxin.ai.jdbc.JdbcConversationStore;
import com.koujunxin.ai.jdbc.JdbcPromptCatalog;
import com.koujunxin.ai.observe.NoOpAiChargePort;
import com.koujunxin.ai.observe.NoOpLangfusePort;
import com.koujunxin.ai.openai.OpenAiCodec;
import com.koujunxin.ai.route.DefaultModelRouter;
import com.koujunxin.ai.sse.SseLimits;
import com.koujunxin.ai.sse.SsePump;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.api.port.SessionAuthorizationPort;
import com.koujunxin.runtime.api.port.SessionStateQueryPort;
import com.koujunxin.runtime.api.port.ToolExecutionPort;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass(JdbcTemplate.class)
public class AiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ModelRouter aiModelRouter() {
        return new DefaultModelRouter();
    }

    @Bean
    @ConditionalOnMissingBean
    OpenAiCodec openAiCodec() {
        return new OpenAiCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    SseLimits sseLimits() {
        return SseLimits.defaults();
    }

    @Bean
    @ConditionalOnMissingBean
    SsePump ssePump(OpenAiCodec codec, SseLimits limits) {
        return new SsePump(codec, limits, Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    @Bean
    @ConditionalOnMissingBean
    AiChargePort aiChargePort() {
        return new NoOpAiChargePort();
    }

    @Bean
    @ConditionalOnMissingBean
    LangfusePort langfusePort() {
        return new NoOpLangfusePort();
    }

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, PlatformTransactionManager.class})
    @ConditionalOnMissingBean
    JdbcPromptCatalog jdbcPromptCatalog(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        return new JdbcPromptCatalog(jdbcTemplate, new TransactionTemplate(transactionManager));
    }

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, PlatformTransactionManager.class})
    @ConditionalOnMissingBean
    JdbcConversationStore jdbcConversationStore(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ObjectProvider<ObjectMapper> objectMapper
    ) {
        ObjectMapper mapper = objectMapper.getIfAvailable(ObjectMapper::new);
        return new JdbcConversationStore(jdbcTemplate, new TransactionTemplate(transactionManager), mapper);
    }

    @Bean
    @ConditionalOnBean({PromptCatalog.class, ConversationStore.class, UpstreamChatClient.class})
    @ConditionalOnMissingBean
    ChatCompletionService chatCompletionService(
            PromptCatalog promptCatalog,
            ConversationStore conversationStore,
            ModelRouter modelRouter,
            UpstreamChatClient upstreamChatClient,
            AiChargePort aiChargePort,
            LangfusePort langfusePort,
            OpenAiCodec openAiCodec,
            SsePump ssePump,
            ObjectProvider<SessionStateQueryPort> sessionState,
            ObjectProvider<SessionAuthorizationPort> sessionAuth,
            ObjectProvider<EntitlementAccessPort> entitlements,
            ObjectProvider<ToolExecutionPort> tools
    ) {
        return new ChatCompletionService(
                promptCatalog,
                conversationStore,
                modelRouter,
                upstreamChatClient,
                aiChargePort,
                langfusePort,
                openAiCodec,
                ssePump,
                Clock.system(ZoneId.of("Asia/Shanghai")),
                sessionState.getIfAvailable(),
                sessionAuth.getIfAvailable(),
                entitlements.getIfAvailable(),
                tools.getIfAvailable(),
                "kjx-strong",
                "kjx-cheap");
    }
}

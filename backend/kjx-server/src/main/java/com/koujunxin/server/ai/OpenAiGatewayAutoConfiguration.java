package com.koujunxin.server.ai;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.koujunxin.ai.chat.ChatCompletionService;
import com.koujunxin.ai.config.AiAutoConfiguration;
import com.koujunxin.ai.openai.OpenAiCodec;
import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.runtime.api.RuntimeAiPort;
import com.koujunxin.runtime.config.RuntimeAutoConfiguration;

@AutoConfiguration(
        after = {
                AiAutoConfiguration.class,
                PlatformAiAutoConfiguration.class
        },
        before = RuntimeAutoConfiguration.class)
@ConditionalOnBean(ChatCompletionService.class)
public class OpenAiGatewayAutoConfiguration {

    @Bean
    @ConditionalOnBean(ChatCompletionService.class)
    @ConditionalOnMissingBean(OpenAiCompatibleController.class)
    OpenAiCompatibleController openAiCompatibleController(
            ChatCompletionService chat,
            RuntimeAccountPort accounts,
            ObjectProvider<OpenAiCodec> codecs) {
        return new OpenAiCompatibleController(chat, accounts, codecs.getIfAvailable(OpenAiCodec::new));
    }

    @Bean
    @ConditionalOnBean(ChatCompletionService.class)
    @ConditionalOnMissingBean(RuntimeAiPort.class)
    RuntimeAiPort chatCompletionRuntimeAiPort(
            ChatCompletionService chat,
            ObjectProvider<OpenAiCodec> codecs) {
        return new ChatCompletionRuntimeAiAdapter(chat, codecs.getIfAvailable(OpenAiCodec::new));
    }
}

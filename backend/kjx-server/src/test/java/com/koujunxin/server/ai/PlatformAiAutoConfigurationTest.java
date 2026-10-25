package com.koujunxin.server.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.koujunxin.ai.api.port.ConversationStore;
import com.koujunxin.ai.api.port.PromptCatalog;
import com.koujunxin.ai.api.port.UpstreamChatClient;
import com.koujunxin.ai.chat.ChatCompletionService;
import com.koujunxin.ai.config.AiAutoConfiguration;
import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.runtime.api.RuntimeAiPort;

class PlatformAiAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PlatformAiAutoConfiguration.class,
                    AiAutoConfiguration.class,
                    OpenAiGatewayAutoConfiguration.class))
            .withBean(PromptCatalog.class, () -> mock(PromptCatalog.class))
            .withBean(ConversationStore.class, () -> mock(ConversationStore.class))
            .withBean(RuntimeAccountPort.class, () -> () -> 1L);

    @Test
    void blankPlatformKeyStillCreatesChatAndRuntimeAiPort() {
        runner.withPropertyValues(
                "kjx.ai.platform.base-url=",
                "kjx.ai.platform.api-key=").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(UpstreamChatClient.class);
                    assertThat(context).hasSingleBean(ChatCompletionService.class);
                    assertThat(context).hasSingleBean(RuntimeAiPort.class);
                    assertThat(context.getBean(UpstreamChatClient.class))
                            .isInstanceOf(PlatformUpstreamChatClient.class);
                });
    }

    @Test
    void missingPlatformPropertiesStillCreatesUpstreamClient() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(UpstreamChatClient.class);
            assertThat(context).hasSingleBean(ChatCompletionService.class);
            assertThat(context).hasSingleBean(RuntimeAiPort.class);
        });
    }
}

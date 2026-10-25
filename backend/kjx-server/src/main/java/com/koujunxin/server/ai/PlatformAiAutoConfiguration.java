package com.koujunxin.server.ai;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.koujunxin.ai.api.port.UpstreamChatClient;
import com.koujunxin.ai.config.AiAutoConfiguration;
import com.koujunxin.ai.openai.OpenAiCodec;
import com.koujunxin.channel.api.DnsLookup;
import com.koujunxin.channel.api.SsrfGuard;

/**
 * Always registers an OpenAI-compatible upstream client so BYOK can call the
 * user-supplied URL when the platform key is blank. PLATFORM still fails at
 * request time unless both platform URL and key are set.
 */
@AutoConfiguration(before = AiAutoConfiguration.class)
@EnableConfigurationProperties(PlatformAiProperties.class)
public class PlatformAiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(UpstreamChatClient.class)
    UpstreamChatClient platformUpstreamChatClient(
            PlatformAiProperties properties,
            ObjectProvider<OpenAiCodec> codecs) {
        PlatformAiProperties resolved = properties == null ? new PlatformAiProperties() : properties;
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        OpenAiCodec codec = codecs.getIfAvailable(OpenAiCodec::new);
        return new PlatformUpstreamChatClient(
                resolved,
                new SsrfGuard(DnsLookup.system()),
                codec,
                new JdkUpstreamHttpTransport(http, Duration.ofSeconds(30)));
    }
}

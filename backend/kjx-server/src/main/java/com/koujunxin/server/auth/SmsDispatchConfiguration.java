package com.koujunxin.server.auth;

import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.koujunxin.account.api.port.SmsDispatchPort;

@Configuration
@EnableConfigurationProperties(KjxSmsProperties.class)
public class SmsDispatchConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "kjx.sms", name = "mode", havingValue = "stub")
    @ConditionalOnMissingBean(SmsDispatchPort.class)
    SmsDispatchPort stubSmsDispatchPort() {
        return new StubSmsDispatchPort();
    }

    @Bean
    @ConditionalOnClass(SmsFactory.class)
    @ConditionalOnProperty(prefix = "kjx.sms", name = "mode", havingValue = "live")
    @ConditionalOnMissingBean(SmsBlendClient.class)
    SmsBlendClient smsBlendClient() {
        return (configId, phone, code) -> {
            SmsBlend blend = SmsFactory.getSmsBlend(configId);
            if (blend == null) {
                SmsResponse failed = new SmsResponse();
                failed.setSuccess(false);
                return failed;
            }
            return blend.sendMessage(phone, code);
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "kjx.sms", name = "mode", havingValue = "live")
    @ConditionalOnMissingBean(SmsDispatchPort.class)
    SmsDispatchPort liveSmsDispatchPort(KjxSmsProperties properties, SmsBlendClient smsBlendClient) {
        String configId = properties.getConfigId() == null ? "" : properties.getConfigId().trim();
        String provider = properties.getProvider() == null ? "" : properties.getProvider().trim();
        if (configId.isEmpty() || provider.isEmpty()) {
            throw new IllegalStateException(
                    "kjx.sms.mode=live requires KJX_SMS_CONFIG_ID and KJX_SMS_PROVIDER; refusing silent degrade");
        }
        return new Sms4jSmsDispatchPort(smsBlendClient, configId, provider);
    }
}

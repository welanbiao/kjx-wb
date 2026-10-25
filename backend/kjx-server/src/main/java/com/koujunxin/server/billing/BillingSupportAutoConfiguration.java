package com.koujunxin.server.billing;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.billing.api.PlatformBilling;
import com.koujunxin.channel.api.ChannelCredentialStore;
import com.koujunxin.channel.jdbc.JdbcChannelCredentialStore;
import com.koujunxin.log.api.port.RequestContextPort;
import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.server.integration.DomainIntegrationAutoConfiguration;

/**
 * 凭据存储和账单接口只在装配层创建，渠道模块不反向依赖启动模块。
 */
@AutoConfiguration(after = DomainIntegrationAutoConfiguration.class)
public class BillingSupportAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(ChannelCredentialStore.class)
    ChannelCredentialStore channelCredentialStore(JdbcTemplate jdbcTemplate) {
        return new JdbcChannelCredentialStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, RuntimeAccountPort.class})
    @ConditionalOnMissingBean(AppBillingController.class)
    AppBillingController appBillingController(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<PlatformBilling> billing,
            ObjectProvider<ChannelCredentialStore> credentials,
            RuntimeAccountPort accounts,
            ObjectProvider<RequestContextPort> contexts,
            @Value("${kjx.billing.payment-hmac-secret:}") String paymentHmacSecret) {
        return new AppBillingController(
                jdbcTemplate, billing, credentials, accounts, contexts, paymentHmacSecret);
    }
}

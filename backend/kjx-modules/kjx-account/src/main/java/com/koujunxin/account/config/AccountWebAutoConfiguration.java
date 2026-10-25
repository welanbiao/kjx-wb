package com.koujunxin.account.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.koujunxin.account.application.AccountApplicationService;
import com.koujunxin.account.web.AppAccountController;
import com.koujunxin.account.web.AppAuthController;

/**
 * 控制器上的 {@code @ConditionalOnBean} 在组件扫描阶段看不到自动配置 Bean，不能用来注册接口。
 * 账号服务存在时在这里创建控制器。
 */
@AutoConfiguration(after = AccountAutoConfiguration.class)
@ConditionalOnBean(AccountApplicationService.class)
public class AccountWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AppAuthController appAuthController(AccountApplicationService accounts) {
        return new AppAuthController(accounts);
    }

    @Bean
    @ConditionalOnMissingBean
    AppAccountController appAccountController(AccountApplicationService accounts) {
        return new AppAccountController(accounts);
    }
}

package com.koujunxin.runtime.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.koujunxin.runtime.api.RuntimeAccountPort;
import com.koujunxin.runtime.session.RuntimeApplicationService;
import com.koujunxin.runtime.breakup.BreakupRuntimeService;
import com.koujunxin.runtime.dreamphone.DreamPhoneRuntimeService;
import com.koujunxin.runtime.web.AppBreakupController;
import com.koujunxin.runtime.web.AppDreamPhoneController;
import com.koujunxin.runtime.web.AppHostProxyController;
import com.koujunxin.runtime.web.AppSaveController;
import com.koujunxin.runtime.web.AppSessionController;
import com.koujunxin.runtime.web.RuntimeExceptionAdvice;

@AutoConfiguration(
        after = RuntimeAutoConfiguration.class,
        afterName = "com.koujunxin.server.integration.DomainIntegrationAutoConfiguration")
@ConditionalOnBean({RuntimeApplicationService.class, RuntimeAccountPort.class})
public class RuntimeWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AppSessionController appSessionController(RuntimeApplicationService runtime, RuntimeAccountPort accounts) {
        return new AppSessionController(runtime, accounts);
    }

    @Bean
    @ConditionalOnMissingBean
    AppSaveController appSaveController(RuntimeApplicationService runtime, RuntimeAccountPort accounts) {
        return new AppSaveController(runtime, accounts);
    }

    @Bean
    @ConditionalOnMissingBean
    AppHostProxyController appHostProxyController(RuntimeApplicationService runtime, RuntimeAccountPort accounts) {
        return new AppHostProxyController(runtime, accounts);
    }

    @Bean
    @ConditionalOnBean(BreakupRuntimeService.class)
    @ConditionalOnMissingBean
    AppBreakupController appBreakupController(BreakupRuntimeService breakup, RuntimeAccountPort accounts) {
        return new AppBreakupController(breakup, accounts);
    }

    @Bean
    @ConditionalOnBean(DreamPhoneRuntimeService.class)
    @ConditionalOnMissingBean
    AppDreamPhoneController appDreamPhoneController(DreamPhoneRuntimeService phone, RuntimeAccountPort accounts) {
        return new AppDreamPhoneController(phone, accounts);
    }

    @Bean
    @ConditionalOnMissingBean
    RuntimeExceptionAdvice runtimeExceptionAdvice() {
        return new RuntimeExceptionAdvice();
    }
}

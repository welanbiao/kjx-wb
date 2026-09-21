package com.koujunxin.server.security;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import org.dromara.common.satoken.handler.SaTokenExceptionHandler;
import org.dromara.common.satoken.utils.StpKit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * app / admin 路由隔离：跨域 token 视为未登录，HTTP 401。
 */
@Configuration
public class DualDomainSaTokenConfig implements WebMvcConfigurer {

    @Bean
    @ConditionalOnMissingBean(SaTokenExceptionHandler.class)
    public SaTokenExceptionHandler saTokenExceptionHandler() {
        return new SaTokenExceptionHandler();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter
                        .match("/app/**")
                        .notMatch("/app/auth/**")
                        .check(r -> StpKit.APP.checkLogin())))
                .addPathPatterns("/app/**")
                .excludePathPatterns("/app/auth/**");

        registry.addInterceptor(new SaInterceptor(handle -> StpKit.ADMIN.checkLogin()))
                .addPathPatterns("/admin-api/**");
    }
}

package com.koujunxin.server.auth;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.account.api.port.CaptchaVerificationPort;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.TACBuilder;
import cloud.tianai.captcha.cache.CacheStore;

/**
 * 手动装配 tianai-captcha 1.5.2 core API。
 * <p>官方 {@code tianai-captcha-springboot-starter:1.5.2} 的 BOM 绑定 Spring Boot 2.2.7，
 * 与本仓库 Spring Boot 3.5 不兼容，不得引入该 starter 自动配置。缓存必须走共享 Redis。</p>
 */
@AutoConfiguration(
        afterName = "org.redisson.spring.starter.RedissonAutoConfigurationV2",
        // AccountAutoConfiguration 会注册 FailClosedCaptchaVerificationPort；必须先于它装配真实实现
        beforeName = "com.koujunxin.account.config.AccountAutoConfiguration")
@ConditionalOnClass(TACBuilder.class)
@ConditionalOnBean(RedissonClient.class)
@ConditionalOnProperty(prefix = "kjx.captcha", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KjxCaptchaProperties.class)
public class TianaiCaptchaConfiguration {

    @Bean
    @ConditionalOnMissingBean(CacheStore.class)
    CacheStore captchaCacheStore(RedissonClient redisson, ObjectMapper objectMapper) {
        return new RedissonCaptchaCacheStore(redisson, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(ImageCaptchaApplication.class)
    ImageCaptchaApplication imageCaptchaApplication(CacheStore captchaCacheStore, KjxCaptchaProperties properties) {
        return TianaiCaptchaSupport.create(captchaCacheStore, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    CaptchaPassStore captchaPassStore(RedissonClient redisson) {
        return new CaptchaPassStore(redisson);
    }

    @Bean
    @ConditionalOnMissingBean(CaptchaVerificationPort.class)
    CaptchaVerificationPort captchaVerificationPort(
            ImageCaptchaApplication imageCaptchaApplication,
            CaptchaPassStore captchaPassStore,
            ObjectMapper objectMapper) {
        return new TianaiCaptchaVerificationPort(imageCaptchaApplication, captchaPassStore, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    CaptchaChallengeController captchaChallengeController(
            ImageCaptchaApplication imageCaptchaApplication,
            KjxCaptchaProperties properties,
            CaptchaVerificationPort captchaVerificationPort) {
        return new CaptchaChallengeController(imageCaptchaApplication, properties, captchaVerificationPort);
    }
}

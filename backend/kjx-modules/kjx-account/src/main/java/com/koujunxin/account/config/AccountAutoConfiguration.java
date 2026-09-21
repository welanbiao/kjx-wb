package com.koujunxin.account.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.account.api.port.AppSessionPort;
import com.koujunxin.account.api.port.BoundMemberQueryPort;
import com.koujunxin.account.api.port.CaptchaVerificationPort;
import com.koujunxin.account.api.port.FailClosedCaptchaVerificationPort;
import com.koujunxin.account.api.port.NoOpSmsDispatchPort;
import com.koujunxin.account.api.port.SmsDispatchPort;
import com.koujunxin.account.application.AccountApplicationService;
import com.koujunxin.account.crypto.PasswordHasher;
import com.koujunxin.account.crypto.PhoneCipher;
import com.koujunxin.account.jdbc.AccountRepository;
import com.koujunxin.account.jdbc.DeviceRepository;
import com.koujunxin.account.jdbc.VerifyCodeAuditRepository;
import com.koujunxin.account.notify.SecurityOutbox;
import com.koujunxin.account.security.LoginLockStore;
import com.koujunxin.account.security.RedisLoginLockStore;
import com.koujunxin.account.security.SaTokenAppSessionPort;
import com.koujunxin.account.sms.RedisSmsCodeStore;
import com.koujunxin.account.sms.RedisSmsRateLimiter;
import com.koujunxin.account.sms.SmsCodeStore;
import com.koujunxin.account.sms.SmsRateLimiter;
import com.koujunxin.common.idempotent.IdempotencyService;
import com.koujunxin.common.idempotent.IdempotencyAutoConfiguration;
import com.koujunxin.notification.outbox.OutboxAutoConfiguration;
import com.koujunxin.notification.outbox.OutboxWriter;

@AutoConfiguration(
        after = {
                JdbcTemplateAutoConfiguration.class,
                IdempotencyAutoConfiguration.class,
                OutboxAutoConfiguration.class
        },
        afterName = "org.redisson.spring.starter.RedissonAutoConfigurationV2")
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(JdbcTemplate.class)
public class AccountAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "kjx.account")
    @ConditionalOnMissingBean
    AccountProperties accountProperties() {
        return AccountProperties.defaults();
    }

    @Bean
    @ConditionalOnMissingBean(PhoneCipher.class)
    PhoneCipher phoneCipher() {
        return PhoneCipher.fromEnv();
    }

    @Bean
    @ConditionalOnMissingBean(PasswordHasher.class)
    PasswordHasher passwordHasher() {
        return new PasswordHasher();
    }

    @Bean
    @ConditionalOnMissingBean(CaptchaVerificationPort.class)
    CaptchaVerificationPort captchaVerificationPort() {
        return new FailClosedCaptchaVerificationPort();
    }

    @Bean
    @ConditionalOnMissingBean(SmsDispatchPort.class)
    SmsDispatchPort smsDispatchPort() {
        return new NoOpSmsDispatchPort();
    }

    @Bean
    @ConditionalOnMissingBean(BoundMemberQueryPort.class)
    BoundMemberQueryPort boundMemberQueryPort() {
        return accountId -> Optional.empty();
    }

    @Bean
    @ConditionalOnMissingBean(AppSessionPort.class)
    AppSessionPort appSessionPort(AccountProperties properties) {
        return new SaTokenAppSessionPort(properties);
    }

    @Bean
    @ConditionalOnMissingBean(SmsCodeStore.class)
    @ConditionalOnBean(RedissonClient.class)
    SmsCodeStore smsCodeStore(RedissonClient redisson) {
        return new RedisSmsCodeStore(redisson);
    }

    @Bean
    @ConditionalOnMissingBean(SmsRateLimiter.class)
    @ConditionalOnBean(RedissonClient.class)
    SmsRateLimiter smsRateLimiter(RedissonClient redisson) {
        return new RedisSmsRateLimiter(redisson);
    }

    @Bean
    @ConditionalOnMissingBean(LoginLockStore.class)
    @ConditionalOnBean(RedissonClient.class)
    LoginLockStore loginLockStore(RedissonClient redisson) {
        return new RedisLoginLockStore(redisson);
    }

    @Bean
    @ConditionalOnMissingBean
    AccountRepository accountRepository(JdbcTemplate jdbcTemplate) {
        return new AccountRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    DeviceRepository deviceRepository(JdbcTemplate jdbcTemplate) {
        return new DeviceRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    VerifyCodeAuditRepository verifyCodeAuditRepository(JdbcTemplate jdbcTemplate) {
        return new VerifyCodeAuditRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(OutboxWriter.class)
    SecurityOutbox securityOutbox(OutboxWriter outboxWriter) {
        return new SecurityOutbox(outboxWriter);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({OutboxWriter.class, IdempotencyService.class, RedissonClient.class})
    AccountApplicationService accountApplicationService(
            AccountProperties properties,
            PhoneCipher phoneCipher,
            PasswordHasher passwordHasher,
            CaptchaVerificationPort captcha,
            SmsCodeStore smsCodes,
            SmsRateLimiter smsRateLimiter,
            SmsDispatchPort smsDispatch,
            LoginLockStore loginLocks,
            AccountRepository accounts,
            DeviceRepository devices,
            VerifyCodeAuditRepository verifyAudit,
            AppSessionPort sessions,
            BoundMemberQueryPort members,
            SecurityOutbox outbox,
            IdempotencyService idempotency,
            ObjectProvider<PlatformTransactionManager> txManagers,
            ObjectMapper objectMapper) {
        PlatformTransactionManager txManager = txManagers.getIfAvailable();
        if (txManager == null) {
            throw new IllegalStateException("PlatformTransactionManager is required for kjx-account");
        }
        return new AccountApplicationService(
                properties,
                Clock.system(ZoneId.of("Asia/Shanghai")),
                phoneCipher,
                passwordHasher,
                captcha,
                smsCodes,
                smsRateLimiter,
                smsDispatch,
                loginLocks,
                accounts,
                devices,
                verifyAudit,
                sessions,
                members,
                outbox,
                idempotency,
                new TransactionTemplate(txManager),
                objectMapper);
    }
}

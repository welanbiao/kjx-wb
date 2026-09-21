package com.koujunxin.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import com.koujunxin.member.application.MemberApplicationService;
import com.koujunxin.member.web.AdminOpsMemberController;
import com.koujunxin.member.web.AdminOpsUnlockController;
import com.koujunxin.member.web.AdminSelfMemberController;
import com.koujunxin.member.web.AppMemberController;
import com.koujunxin.member.web.AppUnlockController;

@SpringBootTest(
        classes = MemberSkeletonContextTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class MemberSkeletonContextTest {

    @Autowired
    ApplicationContext context;

    @Test
    void controllersDoNotStartWithoutApplicationService() {
        assertNull(context.getBeanProvider(MemberApplicationService.class).getIfAvailable());
        assertEquals(0, context.getBeanNamesForType(AppMemberController.class).length);
        assertEquals(0, context.getBeanNamesForType(AdminSelfMemberController.class).length);
        assertEquals(0, context.getBeanNamesForType(AppUnlockController.class).length);
        assertEquals(0, context.getBeanNamesForType(AdminOpsUnlockController.class).length);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
            exclude = {
                    DataSourceAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class
            },
            excludeName = {
                    "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                    "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
                    "org.redisson.spring.starter.RedissonAutoConfigurationV2",
                    "org.dromara.common.redis.config.RedisConfig"
            })
    @ComponentScan(basePackages = "com.koujunxin.member")
    static class App {
    }
}

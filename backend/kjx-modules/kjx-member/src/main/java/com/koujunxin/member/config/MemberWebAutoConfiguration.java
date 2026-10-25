package com.koujunxin.member.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.koujunxin.member.api.port.AppAccountAccessor;
import com.koujunxin.member.api.port.DeptAccessPort;
import com.koujunxin.member.application.MemberApplicationService;
import com.koujunxin.member.unlock.UnlockApplicationService;
import com.koujunxin.member.web.AdminOpsMemberController;
import com.koujunxin.member.web.AdminOpsUnlockController;
import com.koujunxin.member.web.AdminSelfMemberController;
import com.koujunxin.member.web.AppMemberController;
import com.koujunxin.member.web.AppUnlockController;
import com.koujunxin.work.api.WorkCatalogQueryPort;

@AutoConfiguration(after = {MemberAutoConfiguration.class, MemberUnlockAutoConfiguration.class})
public class MemberWebAutoConfiguration {

    @Bean
    @ConditionalOnBean(MemberApplicationService.class)
    @ConditionalOnMissingBean
    AppMemberController appMemberController(MemberApplicationService members, AppAccountAccessor accounts) {
        return new AppMemberController(members, accounts);
    }

    @Bean
    @ConditionalOnBean(MemberApplicationService.class)
    @ConditionalOnMissingBean
    AdminOpsMemberController adminOpsMemberController(MemberApplicationService members, DeptAccessPort access) {
        return new AdminOpsMemberController(members, access);
    }

    @Bean
    @ConditionalOnBean(MemberApplicationService.class)
    @ConditionalOnMissingBean
    AdminSelfMemberController adminSelfMemberController(MemberApplicationService members, DeptAccessPort access) {
        return new AdminSelfMemberController(members, access);
    }

    @Bean
    @ConditionalOnBean(UnlockApplicationService.class)
    @ConditionalOnMissingBean
    AppUnlockController appUnlockController(UnlockApplicationService unlocks, AppAccountAccessor accounts) {
        return new AppUnlockController(unlocks, accounts);
    }

    @Bean
    @ConditionalOnBean(UnlockApplicationService.class)
    @ConditionalOnMissingBean
    AdminOpsUnlockController adminOpsUnlockController(
            UnlockApplicationService unlocks,
            DeptAccessPort access,
            WorkCatalogQueryPort works) {
        return new AdminOpsUnlockController(unlocks, access, works);
    }
}

package com.koujunxin.member.port;

import com.koujunxin.member.api.MemberFailures;
import com.koujunxin.member.api.port.AppAccountAccessor;

public final class StpKitAppAccountAccessor implements AppAccountAccessor {

    @Override
    public long requireAccountId() {
        try {
            if (!org.dromara.common.satoken.utils.StpKit.APP.isLogin()) {
                throw MemberFailures.unauthorized();
            }
            return org.dromara.common.satoken.utils.StpKit.APP.getLoginIdAsLong();
        } catch (RuntimeException ex) {
            if (ex instanceof com.koujunxin.member.api.MemberException member) {
                throw member;
            }
            throw MemberFailures.unauthorized();
        }
    }
}

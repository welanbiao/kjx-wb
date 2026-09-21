package com.koujunxin.work.web;

import org.dromara.common.satoken.utils.StpKit;

public final class StpKitAppAccountAccessor implements AppAccountAccessor {

    @Override
    public long requireAccountId() {
        return StpKit.APP.getLoginIdAsLong();
    }
}

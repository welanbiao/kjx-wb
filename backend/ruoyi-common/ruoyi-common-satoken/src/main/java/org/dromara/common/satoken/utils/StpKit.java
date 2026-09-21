package org.dromara.common.satoken.utils;

import cn.dev33.satoken.stp.StpLogic;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Sa-Token 双域入口。
 * <p>
 * RuoYi-Vue-Plus v5.2.3 默认只有一套 JWT {@code StpLogic}。KJX 按契约使用独立 loginType：
 * {@code app}（C 端 kjx_account）与 {@code admin}（后台 sys_user）。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StpKit {

    public static final String ADMIN_TYPE = "admin";
    public static final String APP_TYPE = "app";

    public static final StpLogic ADMIN = new StpLogic(ADMIN_TYPE);
    public static final StpLogic APP = new StpLogic(APP_TYPE);
}

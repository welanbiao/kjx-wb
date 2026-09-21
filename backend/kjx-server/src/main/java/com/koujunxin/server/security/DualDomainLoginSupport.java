package com.koujunxin.server.security;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.satoken.utils.StpKit;

import java.util.function.Supplier;

/**
 * 双域登录辅助。admin 走 RuoYi {@link LoginHelper}（StpUtil=admin），app 走 {@link StpKit#APP}。
 */
public final class DualDomainLoginSupport {

    private DualDomainLoginSupport() {
    }

    public static String loginAdmin(LoginUser loginUser) {
        if (loginUser.getUserType() == null) {
            loginUser.setUserType(UserType.SYS_USER.getUserType());
        }
        return withContext(() -> {
            StpUtil.setStpLogic(StpKit.ADMIN);
            StpKit.ADMIN.login(loginUser.getLoginId(), new SaLoginModel()
                    .setExtra(LoginHelper.TENANT_KEY, loginUser.getTenantId())
                    .setExtra(LoginHelper.USER_KEY, loginUser.getUserId())
                    .setExtra(LoginHelper.USER_NAME_KEY, loginUser.getUsername())
                    .setExtra(LoginHelper.DEPT_KEY, loginUser.getDeptId())
                    .setExtra(LoginHelper.DEPT_NAME_KEY, loginUser.getDeptName()));
            StpKit.ADMIN.getTokenSession().set(LoginHelper.LOGIN_USER_KEY, loginUser);
            return StpKit.ADMIN.getTokenValue();
        });
    }

    public static String loginApp(LoginUser loginUser) {
        if (loginUser.getUserType() == null) {
            loginUser.setUserType(UserType.APP_USER.getUserType());
        }
        return withContext(() -> {
            StpKit.APP.login(loginUser.getLoginId(), new SaLoginModel()
                    .setExtra(LoginHelper.USER_KEY, loginUser.getUserId())
                    .setExtra(LoginHelper.USER_NAME_KEY, loginUser.getUsername()));
            StpKit.APP.getTokenSession().set(LoginHelper.LOGIN_USER_KEY, loginUser);
            return StpKit.APP.getTokenValue();
        });
    }

    private static String withContext(Supplier<String> action) {
        if (SaManager.getSaTokenContext() != null && SaManager.getSaTokenContext().isValid()) {
            return action.get();
        }
        try {
            return SaTokenContextMockUtil.setMockContext(action::get);
        } finally {
            SaTokenContextMockUtil.clearContext();
        }
    }
}

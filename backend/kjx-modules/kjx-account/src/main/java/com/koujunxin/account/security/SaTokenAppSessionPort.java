package com.koujunxin.account.security;

import java.util.Optional;
import java.util.function.Supplier;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.SaLoginModel;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.satoken.utils.StpKit;

import com.koujunxin.account.api.port.AppSessionPort;
import com.koujunxin.account.config.AccountProperties;
import com.koujunxin.account.crypto.TokenHashes;

/**
 * 对齐 WP-01：C 端使用 {@link StpKit#APP}，loginType=app。
 */
public class SaTokenAppSessionPort implements AppSessionPort {

    private final AccountProperties properties;

    public SaTokenAppSessionPort(AccountProperties properties) {
        this.properties = properties;
    }

    @Override
    public IssuedSession issue(long accountId, String deviceId) {
        return withContext(() -> {
            LoginUser loginUser = appUser(accountId);
            StpKit.APP.login(loginUser.getLoginId(), new SaLoginModel()
                    .setDevice(blankToDefault(deviceId, "web"))
                    .setTimeout(properties.tokenTtlSeconds())
                    .setExtra(LoginHelper.USER_KEY, loginUser.getUserId())
                    .setExtra(LoginHelper.USER_NAME_KEY, loginUser.getUsername()));
            StpKit.APP.getTokenSession().set(LoginHelper.LOGIN_USER_KEY, loginUser);
            String token = StpKit.APP.getTokenValue();
            return new IssuedSession(token, properties.tokenTtlSeconds(), TokenHashes.sha256Hex(token));
        });
    }

    @Override
    public IssuedSession refresh(long accountId, String deviceId) {
        return withContext(() -> {
            StpKit.APP.renewTimeout(properties.tokenTtlSeconds());
            String token = StpKit.APP.getTokenValue();
            if (token == null || token.isBlank()) {
                return issue(accountId, deviceId);
            }
            return new IssuedSession(token, properties.tokenTtlSeconds(), TokenHashes.sha256Hex(token));
        });
    }

    @Override
    public void revokeCurrent() {
        withContext(() -> {
            StpKit.APP.logout();
            return null;
        });
    }

    @Override
    public void revokeDevice(long accountId, String deviceId) {
        withContext(() -> {
            StpKit.APP.logout(accountId, blankToDefault(deviceId, "web"));
            return null;
        });
    }

    @Override
    public void revokeAll(long accountId) {
        withContext(() -> {
            StpKit.APP.logout(accountId);
            return null;
        });
    }

    @Override
    public Optional<Long> currentAccountId() {
        return withContext(() -> {
            if (!StpKit.APP.isLogin()) {
                return Optional.empty();
            }
            return Optional.of(StpKit.APP.getLoginIdAsLong());
        });
    }

    @Override
    public Optional<String> currentTokenHash() {
        return withContext(() -> {
            String token = StpKit.APP.getTokenValue();
            if (token == null || token.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(TokenHashes.sha256Hex(token));
        });
    }

    private static LoginUser appUser(long accountId) {
        LoginUser user = new LoginUser();
        user.setUserId(accountId);
        user.setUserType(UserType.APP_USER.getUserType());
        user.setUsername("account-" + accountId);
        return user;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static <T> T withContext(Supplier<T> action) {
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

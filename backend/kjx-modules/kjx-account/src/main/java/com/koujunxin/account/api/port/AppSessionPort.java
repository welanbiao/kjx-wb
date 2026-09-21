package com.koujunxin.account.api.port;

import java.util.Optional;

/**
 * C 端会话（Sa-Token loginType=app）。实现应对齐 WP-01 {@code StpKit.APP}。
 */
public interface AppSessionPort {

    IssuedSession issue(long accountId, String deviceId);

    IssuedSession refresh(long accountId, String deviceId);

    void revokeCurrent();

    void revokeDevice(long accountId, String deviceId);

    void revokeAll(long accountId);

    Optional<Long> currentAccountId();

    Optional<String> currentTokenHash();

    record IssuedSession(String token, long expireInSeconds, String tokenHash) {
    }
}

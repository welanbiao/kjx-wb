package com.koujunxin.account.security;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.koujunxin.account.api.port.AppSessionPort;
import com.koujunxin.account.config.AccountProperties;
import com.koujunxin.account.crypto.TokenHashes;

/**
 * 测试与无 Sa-Token 上下文时的内存会话。生产应使用 {@link SaTokenAppSessionPort}。
 */
public class InMemoryAppSessionPort implements AppSessionPort {

    private final AccountProperties properties;
    private final Map<String, Session> byToken = new ConcurrentHashMap<>();
    private final Map<String, String> byDevice = new ConcurrentHashMap<>();
    private final ThreadLocal<String> current = new ThreadLocal<>();

    public InMemoryAppSessionPort(AccountProperties properties) {
        this.properties = properties;
    }

    @Override
    public IssuedSession issue(long accountId, String deviceId) {
        revokeDevice(accountId, deviceId);
        String token = UUID.randomUUID().toString().replace("-", "");
        String hash = TokenHashes.sha256Hex(token);
        byToken.put(token, new Session(accountId, deviceId, hash));
        byDevice.put(deviceKey(accountId, deviceId), token);
        current.set(token);
        return new IssuedSession(token, properties.tokenTtlSeconds(), hash);
    }

    @Override
    public IssuedSession refresh(long accountId, String deviceId) {
        return issue(accountId, deviceId);
    }

    @Override
    public void revokeCurrent() {
        String token = current.get();
        if (token != null) {
            Session session = byToken.remove(token);
            if (session != null) {
                byDevice.remove(deviceKey(session.accountId, session.deviceId));
            }
            current.remove();
        }
    }

    @Override
    public void revokeDevice(long accountId, String deviceId) {
        String token = byDevice.remove(deviceKey(accountId, deviceId));
        if (token != null) {
            byToken.remove(token);
            if (token.equals(current.get())) {
                current.remove();
            }
        }
    }

    @Override
    public void revokeAll(long accountId) {
        byToken.entrySet().removeIf(entry -> {
            if (entry.getValue().accountId == accountId) {
                byDevice.remove(deviceKey(accountId, entry.getValue().deviceId));
                return true;
            }
            return false;
        });
        current.remove();
    }

    @Override
    public Optional<Long> currentAccountId() {
        Session session = currentSession();
        return session == null ? Optional.empty() : Optional.of(session.accountId);
    }

    @Override
    public Optional<String> currentTokenHash() {
        Session session = currentSession();
        return session == null ? Optional.empty() : Optional.of(session.tokenHash);
    }

    public void bindCurrent(String token) {
        if (token != null && byToken.containsKey(token)) {
            current.set(token);
        } else {
            current.remove();
        }
    }

    private Session currentSession() {
        String token = current.get();
        return token == null ? null : byToken.get(token);
    }

    private static String deviceKey(long accountId, String deviceId) {
        return accountId + ":" + deviceId;
    }

    private record Session(long accountId, String deviceId, String tokenHash) {
    }
}

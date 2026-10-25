package com.koujunxin.server.account;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.koujunxin.runtime.api.port.AccountAiSettingsPort;

/**
 * 进程内按账号保存 BYOK 设置。不落库、不打密钥日志、无平台默认值。
 */
@Component
public final class InMemoryAccountAiSettingsStore implements AccountAiSettingsPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAccountAiSettingsStore.class);

    private final ConcurrentHashMap<Long, AccountAiSettingsPort.Snapshot> byAccount = new ConcurrentHashMap<>();

    @Override
    public void save(long accountId, String baseUrl, String apiKey, String model) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("account is required");
        }
        String url = requireText(baseUrl, 512);
        String key = requireText(apiKey, 512);
        String modelId = requireText(model, 128);
        byAccount.put(accountId, new AccountAiSettingsPort.Snapshot(url, key, modelId));
        log.info("ai.settings.saved accountId={}", accountId);
    }

    @Override
    public Optional<AccountAiSettingsPort.Snapshot> find(long accountId) {
        if (accountId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(byAccount.get(accountId));
    }

    private static String requireText(String value, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("settings field required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("settings field too long");
        }
        return trimmed;
    }
}

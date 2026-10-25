package com.koujunxin.runtime.api.port;

import java.util.Optional;

/**
 * 账号自备模型通道。只按 accountId 存取，禁止写入日志或默认平台密钥。
 */
public interface AccountAiSettingsPort {

    void save(long accountId, String baseUrl, String apiKey, String model);

    Optional<Snapshot> find(long accountId);

    record Snapshot(String baseUrl, String apiKey, String model) {

        @Override
        public String toString() {
            return "Snapshot[baseUrl=" + baseUrl + ", apiKey=redacted, model=" + model + "]";
        }
    }
}

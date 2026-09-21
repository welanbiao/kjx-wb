package com.koujunxin.ai.api.port;

import com.koujunxin.ai.api.AiChannelMode;
import com.koujunxin.ai.api.TokenUsage;

/**
 * 计费窄端口。PLATFORM 冻结/结算由 WP-30 接线；BYOK 不得调用 freeze。
 */
public interface AiChargePort {

    Hold freezePlatform(ChargeContext context);

    void settle(Hold hold, TokenUsage usage);

    void release(Hold hold, String reason);

    record ChargeContext(
            long accountId,
            String workCode,
            String sessionId,
            String turnId,
            String idempotencyKey,
            AiChannelMode channel
    ) {
    }

    record Hold(long id, boolean active) {
        public static Hold none() {
            return new Hold(0L, false);
        }
    }
}

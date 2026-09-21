package com.koujunxin.ai.observe;

import com.koujunxin.ai.api.TokenUsage;
import com.koujunxin.ai.api.port.AiChargePort;

public final class NoOpAiChargePort implements AiChargePort {

    @Override
    public Hold freezePlatform(ChargeContext context) {
        if (context != null && context.channel() == com.koujunxin.ai.api.AiChannelMode.BYOK) {
            throw new IllegalStateException("BYOK must not freeze platform balance");
        }
        return Hold.none();
    }

    @Override
    public void settle(Hold hold, TokenUsage usage) {
    }

    @Override
    public void release(Hold hold, String reason) {
    }
}

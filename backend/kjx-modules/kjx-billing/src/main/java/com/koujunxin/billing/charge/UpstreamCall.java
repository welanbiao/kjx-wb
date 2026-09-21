package com.koujunxin.billing.charge;

import com.koujunxin.billing.ledger.Money;
import com.koujunxin.channel.api.ResolvedChannel;

public interface UpstreamCall {

    Usage invoke(ResolvedChannel channel);

    record Usage(Money cost, int inputTokens, int outputTokens, String model) {
    }
}

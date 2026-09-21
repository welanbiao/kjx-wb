package com.koujunxin.notification.outbox.consume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExternalSideEffectKeysTest {

    @Test
    void smsProviderKeyIsEventIdAndDoesNotClaimExactlyOnce() {
        assertThat(ExternalSideEffectKeys.smsProviderEventId(" 11111111-1111-1111-1111-111111111111 "))
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThatThrownBy(() -> ExternalSideEffectKeys.smsProviderEventId(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

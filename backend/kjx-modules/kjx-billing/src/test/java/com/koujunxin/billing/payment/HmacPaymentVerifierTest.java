package com.koujunxin.billing.payment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.koujunxin.billing.api.BillingException;

class HmacPaymentVerifierTest {

    @Test
    void acceptsMatchingSignatureAndRejectsTamper() {
        HmacPaymentVerifier verifier = new HmacPaymentVerifier("sandbox-sign-key");
        String payload = "ALIPAY|trade-1|10.000000|3";
        String signature = verifier.sign(payload);
        assertDoesNotThrow(() -> verifier.verify(payload, signature));
        assertThrows(BillingException.class, () -> verifier.verify(payload + "|x", signature));
    }
}

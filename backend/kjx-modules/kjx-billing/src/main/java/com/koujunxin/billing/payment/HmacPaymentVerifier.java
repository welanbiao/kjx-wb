package com.koujunxin.billing.payment;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.koujunxin.billing.api.BillingException;

public final class HmacPaymentVerifier {

    private final byte[] secret;

    public HmacPaymentVerifier(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("payment secret rejected");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String payload, String signature) {
        if (payload == null || signature == null || signature.isBlank()) {
            throw BillingException.rejected("支付验签失败");
        }
        byte[] expected = signBytes(payload);
        byte[] actual;
        try {
            actual = HexFormat.of().parseHex(signature.trim());
        } catch (IllegalArgumentException ex) {
            throw BillingException.rejected("支付验签失败");
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw BillingException.rejected("支付验签失败");
        }
    }

    public String sign(String payload) {
        return HexFormat.of().formatHex(signBytes(payload));
    }

    private byte[] signBytes(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("payment verifier unavailable", ex);
        }
    }
}

package com.koujunxin.runtime.ledger;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.koujunxin.runtime.api.RuntimeFailure;

/**
 * 存档 HMAC。密钥由装配注入，禁止写入日志。
 */
public final class SaveSigner {

    private final byte[] secret;

    public SaveSigner(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("runtime save secret rejected");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(
            long accountId,
            String sessionId,
            String workCode,
            String slot,
            String schemaVersion,
            String contentVersion,
            String checksum) {
        return HexFormat.of().formatHex(signBytes(canonical(
                accountId, sessionId, workCode, slot, schemaVersion, contentVersion, checksum)));
    }

    public void verify(
            long accountId,
            String sessionId,
            String workCode,
            String slot,
            String schemaVersion,
            String contentVersion,
            String checksum,
            String signature) {
        if (signature == null || signature.isBlank()) {
            throw RuntimeFailure.checksum("save signature missing");
        }
        byte[] expected = signBytes(canonical(
                accountId, sessionId, workCode, slot, schemaVersion, contentVersion, checksum));
        byte[] actual;
        try {
            actual = HexFormat.of().parseHex(signature.trim());
        } catch (IllegalArgumentException ex) {
            throw RuntimeFailure.checksum("save signature invalid");
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw RuntimeFailure.checksum("save signature mismatch");
        }
    }

    private static String canonical(
            long accountId,
            String sessionId,
            String workCode,
            String slot,
            String schemaVersion,
            String contentVersion,
            String checksum) {
        return accountId + "|" + sessionId + "|" + workCode + "|" + slot + "|"
                + schemaVersion + "|" + contentVersion + "|" + checksum;
    }

    private byte[] signBytes(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("save signer unavailable", ex);
        }
    }
}

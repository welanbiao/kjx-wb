package com.koujunxin.account.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM 加密手机号 + HMAC-SHA256 唯一索引。密钥仅环境/KMS 注入，不进 Git。
 */
public final class PhoneCipher {

    public static final String AES_KEY_ENV = "PHONE_CRYPTO_KEY";
    public static final String HMAC_KEY_ENV = "PHONE_HMAC_SECRET";

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey aesKey;
    private final byte[] hmacKey;

    public PhoneCipher(byte[] aesKeyBytes, byte[] hmacKeyBytes) {
        Objects.requireNonNull(aesKeyBytes, "aes key");
        Objects.requireNonNull(hmacKeyBytes, "hmac key");
        if (aesKeyBytes.length != 32) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes");
        }
        if (hmacKeyBytes.length < 16) {
            throw new IllegalArgumentException("HMAC key must be at least 16 bytes");
        }
        this.aesKey = new SecretKeySpec(aesKeyBytes.clone(), "AES");
        this.hmacKey = hmacKeyBytes.clone();
    }

    public static PhoneCipher fromEnv() {
        return new PhoneCipher(decodeKey(AES_KEY_ENV), decodeKey(HMAC_KEY_ENV));
    }

    public static PhoneCipher fromBase64(String aesKeyBase64, String hmacKeyBase64) {
        return new PhoneCipher(decodeBase64(aesKeyBase64, AES_KEY_ENV), decodeBase64(hmacKeyBase64, HMAC_KEY_ENV));
    }

    public String encrypt(String phone) {
        String normalized = requirePhone(phone);
        byte[] iv = new byte[GCM_IV_LENGTH];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("phone encrypt failed", ex);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("ciphertext is required");
        }
        byte[] packed = Base64.getDecoder().decode(ciphertext);
        if (packed.length <= GCM_IV_LENGTH) {
            throw new IllegalArgumentException("ciphertext too short");
        }
        byte[] iv = Arrays.copyOfRange(packed, 0, GCM_IV_LENGTH);
        byte[] body = Arrays.copyOfRange(packed, GCM_IV_LENGTH, packed.length);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(body);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("phone decrypt failed", ex);
        }
    }

    public String hmacHex(String phone) {
        return hmacUtf8(requirePhone(phone));
    }

    public String hmacUtf8(String value) {
        if (value == null) {
            throw new IllegalArgumentException("hmac input is required");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("hmac failed", ex);
        }
    }

    public static String mask(String phone) {
        String normalized = requirePhone(phone);
        return normalized.substring(0, 3) + "****" + normalized.substring(7);
    }

    public static boolean isPhone(String value) {
        return value != null && PHONE.matcher(value).matches();
    }

    public static String requirePhone(String phone) {
        if (!isPhone(phone)) {
            throw new IllegalArgumentException("invalid phone");
        }
        return phone;
    }

    private static byte[] decodeKey(String envName) {
        String raw = System.getenv(envName);
        if (raw == null || raw.isBlank()) {
            raw = System.getProperty(envName);
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(envName + " must be injected, never committed");
        }
        String normalized = raw.trim();
        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException ignored) {
            return normalized.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static byte[] decodeBase64(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(name + " must be injected (base64), never committed");
        }
        return Base64.getDecoder().decode(raw.trim());
    }
}

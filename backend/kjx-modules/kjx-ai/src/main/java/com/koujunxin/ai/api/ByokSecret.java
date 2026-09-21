package com.koujunxin.ai.api;

import java.util.Arrays;

/**
 * BYOK 密钥持有者。toString 永不回显，用完应 {@link #wipe()}。
 */
public final class ByokSecret {

    private final char[] value;

    public ByokSecret(char[] value) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("BYOK secret is required");
        }
        this.value = Arrays.copyOf(value, value.length);
    }

    public static ByokSecret of(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("BYOK secret is required");
        }
        return new ByokSecret(secret.toCharArray());
    }

    public String reveal() {
        return new String(value);
    }

    public void wipe() {
        Arrays.fill(value, '\0');
    }

    @Override
    public String toString() {
        return "ByokSecret[redacted]";
    }
}

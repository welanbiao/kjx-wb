package com.koujunxin.channel.api;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 凭据引用。只接受 ref: 形式，拒绝明文 Key。
 */
public record SecretRef(String value) {

    private static final Pattern REF = Pattern.compile("^ref:[a-z0-9][a-z0-9._/-]{0,120}$");

    public SecretRef {
        value = require(value);
    }

    public static String require(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("secretRef rejected");
        }
        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (value.isEmpty()
                || value.length() > 128
                || lower.contains("sk-")
                || lower.contains("akia")
                || lower.contains("api_key")
                || lower.contains("bearer ")
                || lower.contains("secret_key")
                || !REF.matcher(value).matches()) {
            throw new IllegalArgumentException("secretRef rejected");
        }
        return value;
    }

    @Override
    public String toString() {
        return "SecretRef[redacted]";
    }
}

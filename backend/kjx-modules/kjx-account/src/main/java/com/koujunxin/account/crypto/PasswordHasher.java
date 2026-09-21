package com.koujunxin.account.crypto;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2id 密码哈希（Spring Security 5.8 默认参数）。明文永不落库。
 */
public final class PasswordHasher {

    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Set<String> WEAK = Set.of(
            "password", "password1", "password12", "12345678", "123456789",
            "qwerty123", "abc12345", "abc123456", "11111111", "00000000",
            "iloveyou", "admin123", "letmein1", "welcome1", "monkey12");

    private final PasswordEncoder encoder;

    public PasswordHasher() {
        this(Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
    }

    public PasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    public void validatePolicy(String raw) {
        if (raw == null || raw.length() < 8 || raw.length() > 32) {
            throw new IllegalArgumentException("密码须为 8~32 位且包含字母和数字");
        }
        if (!LETTER.matcher(raw).find() || !DIGIT.matcher(raw).find()) {
            throw new IllegalArgumentException("密码须为 8~32 位且包含字母和数字");
        }
        if (WEAK.contains(raw.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("密码过于常见，请更换");
        }
    }

    public String hash(String raw) {
        validatePolicy(raw);
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String hashed) {
        if (raw == null || hashed == null) {
            return false;
        }
        return encoder.matches(raw, hashed);
    }
}

package com.koujunxin.notification.outbox.support;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * last_error 脱敏截断，长度不超过 DDL VARCHAR(500)。
 */
public final class ErrorDigest {

    public static final int MAX_LENGTH = 500;

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[a-z0-9._\\-+/=]+");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(authorization|token|password|secret|apikey|api_key|access_token|refresh_token|verify[_-]?code)\\s*[=:]\\s*[^\\s,;]+");
    private static final Pattern LONG_SECRET = Pattern.compile("(?i)(sk-|ak-)[a-z0-9]{8,}");

    private ErrorDigest() {
    }

    public static String of(Throwable error) {
        String raw = error == null ? "unknown" : error.getClass().getSimpleName() + ": " + error.getMessage();
        return sanitize(raw);
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        String value = raw.replace('\n', ' ').replace('\r', ' ');
        value = BEARER.matcher(value).replaceAll("Bearer ***");
        value = KEY_VALUE.matcher(value).replaceAll("$1=***");
        value = LONG_SECRET.matcher(value).replaceAll("$1***");
        value = PHONE.matcher(value).replaceAll("1**********");
        if (value.length() > MAX_LENGTH) {
            value = value.substring(0, MAX_LENGTH);
        }
        return value.toLowerCase(Locale.ROOT).contains("authorization") && !value.contains("***")
                ? value
                : value;
    }
}

package com.koujunxin.log.redact;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 入库 / 序列化前统一脱敏。递归处理 Map、List、数组与异常文本。
 */
public final class LogRedactor {

    public static final String REDACTED = "[REDACTED]";
    public static final String SECRET_PLACEHOLDER_PREFIX = "[[REDACTED:";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "phone", "mobile", "phonenumber", "phone_number",
            "verifycode", "verify_code", "smscode", "sms_code", "captcha_code", "captchacode",
            "authorization", "cookie", "setcookie", "set_cookie", "cookies",
            "apikey", "api_key", "access_token", "accesstoken", "refresh_token", "refreshtoken",
            "id_token", "idtoken", "secret", "secretref", "secret_ref",
            "password", "token", "credential", "private_key", "privatekey");

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._\\-+/=]+");
    private static final Pattern API_KEY = Pattern.compile("(?i)(sk-|ak-)[A-Za-z0-9._\\-+/=]{8,}");
    private static final Pattern COOKIE_PAIR = Pattern.compile("(?i)(cookie|set-cookie)\\s*[:=]\\s*[^\\r\\n]+");
    private static final Pattern AUTH_PAIR = Pattern.compile(
            "(?i)(authorization|api[_-]?key|access_token|refresh_token|verify[_-]?code)\\s*[=:]\\s*[^\\s,;]+");
    private static final Pattern SECRET_REF_OBJECT = Pattern.compile("(?i)secretRef");

    public Object redact(Object value) {
        return redactNode(value, "");
    }

    public String redactText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String value = BEARER.matcher(text).replaceAll("Bearer " + REDACTED);
        value = COOKIE_PAIR.matcher(value).replaceAll("$1=" + REDACTED);
        value = AUTH_PAIR.matcher(value).replaceAll("$1=" + REDACTED);
        value = API_KEY.matcher(value).replaceAll("$1" + REDACTED);
        Matcher phones = PHONE.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (phones.find()) {
            phones.appendReplacement(buffer, Matcher.quoteReplacement(maskPhone(phones.group(1))));
        }
        phones.appendTail(buffer);
        return buffer.toString();
    }

    public String redactThrowable(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (depth > 0) {
                builder.append(" | caused by ");
            }
            builder.append(current.getClass().getSimpleName()).append(": ");
            builder.append(redactText(current.getMessage() == null ? "" : current.getMessage()));
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private Object redactNode(Object node, String path) {
        if (node == null) {
            return null;
        }
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                copy.put(key, redactByKey(key, entry.getValue(), path));
            }
            return copy;
        }
        if (node instanceof Collection<?> collection) {
            return collection.stream().map(item -> redactNode(item, path)).toList();
        }
        if (node.getClass().isArray() && !(node instanceof byte[]) && !(node instanceof char[])) {
            int length = java.lang.reflect.Array.getLength(node);
            Object[] copy = new Object[length];
            for (int i = 0; i < length; i++) {
                copy[i] = redactNode(java.lang.reflect.Array.get(node, i), path + "[" + i + "]");
            }
            return List.of(copy);
        }
        if (node instanceof Throwable throwable) {
            return redactThrowable(throwable);
        }
        if (node instanceof CharSequence text) {
            return redactText(text.toString());
        }
        return node;
    }

    private Object redactByKey(String key, Object value, String path) {
        String normalized = normalizeKey(key);
        if (isSecretRef(normalized, key)) {
            return secretPlaceholder(value);
        }
        if (isSensitiveKey(normalized)) {
            return REDACTED;
        }
        return redactNode(value, path + "." + key);
    }

    private static boolean isSensitiveKey(String normalized) {
        if (SENSITIVE_KEYS.contains(normalized)) {
            return true;
        }
        return normalized.contains("password")
                || normalized.contains("authorization")
                || normalized.contains("apikey")
                || normalized.contains("verify")
                || normalized.endsWith("secret")
                || normalized.contains("cookie");
    }

    private static boolean isSecretRef(String normalized, String original) {
        return "secretref".equals(normalized)
                || "secret_ref".equals(normalized)
                || SECRET_REF_OBJECT.matcher(original).matches();
    }

    private static String secretPlaceholder(Object value) {
        String raw = value == null ? "" : String.valueOf(value);
        if (PHONE.matcher(raw).find() || API_KEY.matcher(raw).find() || BEARER.matcher(raw).find()
                || raw.toLowerCase(Locale.ROOT).contains("cookie")
                || raw.toLowerCase(Locale.ROOT).contains("verify")) {
            return SECRET_PLACEHOLDER_PREFIX + "secret_id]]";
        }
        String id = raw.replaceAll("[^A-Za-z0-9._-]", "");
        if (id.isBlank() || id.length() > 64) {
            id = "secret_id";
        }
        return SECRET_PLACEHOLDER_PREFIX + id + "]]";
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) {
            return "1**********";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    public static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase(Locale.ROOT).replace("-", "_");
    }
}

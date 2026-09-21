package com.koujunxin.log.redact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogRedactorTest {

    private final LogRedactor redactor = new LogRedactor();

    @ParameterizedTest
    @ValueSource(strings = {
            "phone", "mobile", "verifyCode", "verify_code", "Authorization", "authorization",
            "apiKey", "api_key", "API-Key", "secretRef", "secret_ref", "Cookie", "Set-Cookie",
            "password", "access_token", "refresh_token"
    })
    void sensitiveKeysAreFullyRedacted(String key) {
        Map<String, Object> input = Map.of(key, "13812345678-secret-value");
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) redactor.redact(input);
        Object value = out.get(key);
        if ("secretRef".equals(key) || "secret_ref".equals(key)) {
            assertThat(String.valueOf(value)).startsWith("[[REDACTED:");
        } else {
            assertThat(value).isEqualTo(LogRedactor.REDACTED);
        }
        assertThat(String.valueOf(value)).doesNotContain("13812345678");
        assertThat(String.valueOf(value)).doesNotContain("secret-value");
    }

    @Test
    void nestedObjectsAndListsAreRedacted() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("authorization", "Bearer super-secret-token");
        nested.put("headers", Map.of("Cookie", "sid=abc", "X-Request-Id", "req-1"));
        nested.put("items", List.of(Map.of("apiKey", "sk-abcdefghijklmnop"), "hello"));
        nested.put("note", "call 13900139000 please");
        nested.put("secretRef", "truth-field-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) redactor.redact(nested);
        assertThat(out.get("authorization")).isEqualTo(LogRedactor.REDACTED);
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) out.get("headers");
        assertThat(headers.get("Cookie")).isEqualTo(LogRedactor.REDACTED);
        assertThat(headers.get("X-Request-Id")).isEqualTo("req-1");
        assertThat(String.valueOf(out.get("note"))).contains("139****9000").doesNotContain("13900139000");
        assertThat(String.valueOf(out.get("secretRef"))).isEqualTo("[[REDACTED:truth-field-1]]");
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) out.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) items.get(0);
        assertThat(first.get("apiKey")).isEqualTo(LogRedactor.REDACTED);
    }

    @Test
    void exceptionTextIsRedacted() {
        RuntimeException cause = new RuntimeException("Authorization: Bearer abcdefg cookie=sid=1 phone=13812345678");
        IllegalStateException ex = new IllegalStateException("verify_code=123456 sk-ABCDEFGH12345678", cause);
        String text = redactor.redactThrowable(ex);
        assertThat(text).doesNotContain("13812345678");
        assertThat(text).doesNotContain("123456");
        assertThat(text).doesNotContain("abcdefg");
        assertThat(text).doesNotContain("sk-ABCDEFGH12345678");
        assertThat(text).contains(LogRedactor.REDACTED);
        assertThat(text).contains("caused by");
    }

    @RepeatedTest(40)
    void randomNestedGraphsNeverLeakPlainSecrets(RepetitionInfo info) {
        Random random = new Random(20260921L + info.getCurrentRepetition());
        Object graph = randomNode(random, 0);
        Object redacted = redactor.redact(graph);
        String dumped = String.valueOf(redacted);
        assertThat(dumped).doesNotContain("13812345678");
        assertThat(dumped).doesNotContain("sk-LIVESECRETKEY99");
        assertThat(dumped).doesNotContain("Bearer livelongtoken");
        assertThat(dumped).doesNotContain("sid=rawcookie");
        assertThat(dumped).doesNotContain("verify-code-999999");
    }

    private Object randomNode(Random random, int depth) {
        if (depth > 4) {
            return switch (random.nextInt(4)) {
                case 0 -> "safe-" + random.nextInt(100);
                case 1 -> "13812345678";
                case 2 -> 42;
                default -> "Bearer livelongtoken";
            };
        }
        int kind = random.nextInt(3);
        if (kind == 0) {
            Map<String, Object> map = new LinkedHashMap<>();
            String[] keys = {"note", "phone", "Authorization", "api_key", "secretRef", "Cookie", "path", "verify_code"};
            int size = 1 + random.nextInt(4);
            for (int i = 0; i < size; i++) {
                String key = keys[random.nextInt(keys.length)];
                map.put(key, secretOrChild(random, key, depth));
            }
            return map;
        }
        if (kind == 1) {
            List<Object> list = new ArrayList<>();
            int size = 1 + random.nextInt(3);
            for (int i = 0; i < size; i++) {
                list.add(randomNode(random, depth + 1));
            }
            return list;
        }
        return secretOrChild(random, "note", depth);
    }

    private Object secretOrChild(Random random, String key, int depth) {
        String normalized = LogRedactor.normalizeKey(key);
        if (normalized.contains("phone")) {
            return "13812345678";
        }
        if (normalized.contains("authorization")) {
            return "Bearer livelongtoken";
        }
        if (normalized.contains("api")) {
            return "sk-LIVESECRETKEY99";
        }
        if (normalized.contains("cookie")) {
            return "sid=rawcookie";
        }
        if (normalized.contains("verify")) {
            return "verify-code-999999";
        }
        if (normalized.contains("secret")) {
            return "truth-field-1";
        }
        if (random.nextBoolean() && depth < 3) {
            return randomNode(random, depth + 1);
        }
        return "ok";
    }
}

package com.koujunxin.common.idempotent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CanonicalRequestHashTest {

    @Test
    void objectKeyOrderDoesNotChangeHash() {
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("workCode", "breakup-editor");
        left.put("accountId", 42);
        Map<String, Object> right = new LinkedHashMap<>();
        right.put("accountId", 42);
        right.put("workCode", "breakup-editor");

        assertThat(CanonicalRequestHash.sha256Hex(left))
                .isEqualTo(CanonicalRequestHash.sha256Hex(right))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void nestedObjectsAreSortedRecursively() {
        Map<String, Object> left = Map.of("outer", Map.of("b", 1, "a", 2));
        Map<String, Object> right = Map.of("outer", new LinkedHashMap<>(Map.of("a", 2, "b", 1)));
        assertThat(CanonicalRequestHash.sha256Hex(left)).isEqualTo(CanonicalRequestHash.sha256Hex(right));
    }

    @Test
    void arrayOrderIsSignificant() {
        String first = CanonicalRequestHash.sha256Hex(Map.of("ids", List.of(1, 2)));
        String second = CanonicalRequestHash.sha256Hex(Map.of("ids", List.of(2, 1)));
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void jsonStringAndMapProduceSameHash() {
        String fromMap = CanonicalRequestHash.sha256Hex(Map.of("scene", "unlock", "ref", 9));
        String fromJson = CanonicalRequestHash.sha256HexJson("{\"ref\":9,\"scene\":\"unlock\"}");
        assertThat(fromMap).isEqualTo(fromJson);
    }

    @Test
    void differentPayloadsDiffer() {
        assertThat(CanonicalRequestHash.sha256Hex(Map.of("x", 1)))
                .isNotEqualTo(CanonicalRequestHash.sha256Hex(Map.of("x", 2)));
    }
}

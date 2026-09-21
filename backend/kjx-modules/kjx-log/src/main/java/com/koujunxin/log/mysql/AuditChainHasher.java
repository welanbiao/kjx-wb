package com.koujunxin.log.mysql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * hash_n = SHA256(prev_hash + canonical_json(payload))
 */
public final class AuditChainHasher {

    public static final String GENESIS = "0".repeat(64);

    private final ObjectMapper canonical;

    public AuditChainHasher() {
        this.canonical = JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .addModule(new JavaTimeModule())
                .build();
    }

    public String hash(String prevHash, Map<String, Object> payload) {
        String prev = prevHash == null || prevHash.isBlank() ? GENESIS : prevHash;
        try {
            String json = canonical.writeValueAsString(payload == null ? Map.of() : payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(prev.getBytes(StandardCharsets.UTF_8));
            digest.update(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("audit hash failed", ex);
        }
    }

    public Map<String, Object> canonicalPayload(AuditRow row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("logId", row.logId());
        payload.put("ts", row.ts());
        payload.put("traceId", row.traceId());
        payload.put("requestId", row.requestId());
        payload.put("adminId", row.adminId());
        payload.put("adminName", row.adminName());
        payload.put("action", row.action());
        payload.put("targetType", row.targetType());
        payload.put("targetId", row.targetId());
        payload.put("result", row.result());
        payload.put("reason", row.reason());
        return payload;
    }

    public record AuditRow(
            String logId,
            String ts,
            String traceId,
            String requestId,
            long adminId,
            String adminName,
            String action,
            String targetType,
            String targetId,
            String result,
            String reason
    ) {
    }
}

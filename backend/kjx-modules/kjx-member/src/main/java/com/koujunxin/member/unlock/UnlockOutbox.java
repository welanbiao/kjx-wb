package com.koujunxin.member.unlock;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxEnvelope;
import com.koujunxin.notification.outbox.OutboxWriter;

/**
 * 权益/解锁副作用只写 Outbox 完整信封，禁止事务内直接外呼。
 */
public class UnlockOutbox {

    private final OutboxWriter outbox;

    public UnlockOutbox(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void granted(long entitlementId, long unlockRecordId, Long accountId, long memberId, long deptId,
                        long workId, String workCode, String source, String workVersion,
                        int beforeUnlocked, int afterUnlocked, int beforeRemain, int afterRemain,
                        Instant occurredAt, String requestId, String idem) {
        Map<String, Object> payload = entitlementPayload(
                "GRANTED", source, accountId, memberId, deptId, workId, workCode, workVersion,
                entitlementId, unlockRecordId, beforeUnlocked, afterUnlocked, beforeRemain, afterRemain, occurredAt);
        append("entitlement", String.valueOf(entitlementId), "entitlement.granted", payload, requestId, idem, occurredAt);
        recorded("SUCCESS", source, accountId, memberId, deptId, workId, workCode, unlockRecordId, null,
                beforeUnlocked, afterUnlocked, occurredAt, requestId, idem);
    }

    public void revoked(long entitlementId, long unlockRecordId, Long accountId, long memberId, long deptId,
                        long workId, String workCode, String source, String workVersion,
                        int beforeUnlocked, int afterUnlocked, int beforeRemain, int afterRemain,
                        Instant occurredAt, String requestId, String idem) {
        Map<String, Object> payload = entitlementPayload(
                "REVOKED", source, accountId, memberId, deptId, workId, workCode, workVersion,
                entitlementId, unlockRecordId, beforeUnlocked, afterUnlocked, beforeRemain, afterRemain, occurredAt);
        append("entitlement", String.valueOf(memberId), "entitlement.revoked", payload, requestId, idem, occurredAt);
        recorded("SUCCESS", source, accountId, memberId, deptId, workId, workCode, unlockRecordId, null,
                beforeUnlocked, afterUnlocked, occurredAt, requestId, idem);
    }

    public void recorded(String result, String source, Long accountId, long memberId, long deptId,
                         long workId, String workCode, long unlockRecordId, String failReason,
                         int beforeUnlocked, int afterUnlocked, Instant occurredAt, String requestId, String idem) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", OutboxAppendCommand.CURRENT_SCHEMA);
        payload.put("result", result);
        payload.put("source", source);
        if (accountId != null && accountId > 0) {
            payload.put("accountId", accountId);
        }
        payload.put("memberId", memberId);
        payload.put("deptId", deptId);
        payload.put("workId", workId);
        if (workCode != null && !workCode.isBlank()) {
            payload.put("workCode", workCode);
        }
        payload.put("unlockRecordId", unlockRecordId);
        if (failReason != null && !failReason.isBlank()) {
            payload.put("failReason", failReason);
        }
        payload.put("beforeUnlockedCount", beforeUnlocked);
        payload.put("afterUnlockedCount", afterUnlocked);
        payload.put("occurredAt", OutboxEnvelope.formatOccurredAt(occurredAt));
        append("unlock", String.valueOf(unlockRecordId), "unlock.recorded", payload, requestId, idem, occurredAt);
    }

    private Map<String, Object> entitlementPayload(
            String action, String source, Long accountId, long memberId, long deptId, long workId, String workCode,
            String workVersion, long entitlementId, long unlockRecordId,
            int beforeUnlocked, int afterUnlocked, int beforeRemain, int afterRemain, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", OutboxAppendCommand.CURRENT_SCHEMA);
        payload.put("action", action);
        payload.put("source", source);
        if (accountId != null && accountId > 0) {
            payload.put("accountId", accountId);
        }
        payload.put("memberId", memberId);
        payload.put("deptId", deptId);
        payload.put("workId", workId);
        payload.put("workCode", workCode);
        if (workVersion != null && !workVersion.isBlank()) {
            payload.put("workVersion", workVersion);
        }
        payload.put("entitlementId", entitlementId);
        payload.put("unlockRecordId", unlockRecordId);
        payload.put("beforeUnlockedCount", beforeUnlocked);
        payload.put("afterUnlockedCount", afterUnlocked);
        payload.put("beforeRemainCount", beforeRemain);
        payload.put("afterRemainCount", afterRemain);
        payload.put("occurredAt", OutboxEnvelope.formatOccurredAt(occurredAt));
        return payload;
    }

    private void append(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload,
                        String requestId, String idem, Instant occurredAt) {
        outbox.append(new OutboxAppendCommand(
                UUID.randomUUID().toString(),
                aggregateType,
                aggregateId,
                eventType,
                OutboxAppendCommand.CURRENT_SCHEMA,
                occurredAt,
                null,
                requestId,
                idem,
                payload));
    }
}

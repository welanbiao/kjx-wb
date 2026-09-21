package com.koujunxin.runtime.session;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.api.WorkRuntimeManifest;
import com.koujunxin.runtime.api.WorkRuntimePort;
import com.koujunxin.runtime.ledger.CanonicalJson;
import com.koujunxin.runtime.api.port.SessionAuthorizationPort;
import com.koujunxin.runtime.api.port.SessionStateCommandPort;
import com.koujunxin.runtime.api.port.SessionStateQueryPort;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;
import com.koujunxin.runtime.api.dto.StateMutation;
import com.koujunxin.runtime.api.error.SessionNotOwnedException;

/**
 * SessionState 三端口实现。按 SessionRef 读写，不检查会员 VALID。
 */
public final class SessionStateService implements SessionStateQueryPort, SessionStateCommandPort, SessionAuthorizationPort {

    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";
    public static final String INITIAL_PAYLOAD = "{\"flags\":{},\"reached\":[\"1\"],\"stack\":[\"home\"]}";

    private final RuntimeStore store;
    private final WorkRuntimePort works;

    public SessionStateService(RuntimeStore store, WorkRuntimePort works) {
        this.store = store;
        this.works = works;
    }

    public SessionRecord create(long accountId, String workCode) {
        WorkRuntimeManifest manifest = works.require(workCode);
        CanonicalJson.requireSchema(manifest.schemaVersion());
        Instant now = Instant.now();
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String stage = "1";
        String checksum = CanonicalJson.checksum(manifest.schemaVersion(), manifest.contentVersion(), stage, INITIAL_PAYLOAD);
        SessionRecord row = new SessionRecord(
                0L,
                sessionId,
                accountId,
                manifest.workCode(),
                manifest.workId(),
                manifest.form(),
                stage,
                manifest.schemaVersion(),
                manifest.contentVersion(),
                INITIAL_PAYLOAD,
                checksum,
                ACTIVE,
                0,
                now,
                now);
        store.insertSession(row);
        return store.findSession(sessionId).orElse(row);
    }

    @Override
    public SessionStateSnapshot load(SessionRef session) {
        SessionRecord row = requireSession(session.sessionId());
        assertWork(row, session.workCode());
        CanonicalJson.verifyChecksum(row.schemaVersion(), row.contentVersion(), row.stage(), row.payloadJson(), row.checksum());
        return toSnapshot(row);
    }

    @Override
    public SessionStateSnapshot apply(SessionRef session, StateMutation mutation) {
        SessionRecord row = requireSession(session.sessionId());
        assertWork(row, session.workCode());
        if (ENDED.equals(row.status())) {
            throw RuntimeFailure.sessionEnded();
        }
        CanonicalJson.requireSchema(mutation.schemaVersion());
        if (!row.schemaVersion().equals(mutation.schemaVersion())) {
            throw RuntimeFailure.schema("schemaVersion does not match session");
        }
        CanonicalJson.verifyChecksum(row.schemaVersion(), row.contentVersion(), row.stage(), row.payloadJson(), row.checksum());
        Applied applied = applyMutation(row, mutation);
        SessionRecord next = new SessionRecord(
                row.id(),
                row.sessionId(),
                row.accountId(),
                row.workCode(),
                row.workId(),
                row.form(),
                applied.stage(),
                row.schemaVersion(),
                row.contentVersion(),
                applied.payloadJson(),
                applied.checksum(),
                row.status(),
                row.version(),
                row.createdAt(),
                Instant.now());
        if (!store.updateSession(next, row.version())) {
            throw RuntimeFailure.conflict("session was updated on another device");
        }
        return toSnapshot(store.findSession(row.sessionId()).orElse(next));
    }

    @Override
    public void requireOwner(long accountId, SessionRef session) {
        SessionRecord row = store.findSession(session.sessionId())
                .orElseThrow(RuntimeFailure::crossSession);
        if (row.accountId() != accountId) {
            throw new SessionNotOwnedException(accountId, session.sessionId());
        }
        if (!row.workCode().equals(session.workCode())) {
            throw RuntimeFailure.crossWork();
        }
    }

    public SessionRecord requireOwned(long accountId, SessionRef session) {
        requireOwner(accountId, session);
        return requireSession(session.sessionId());
    }

    public SessionRecord end(long accountId, SessionRef session) {
        SessionRecord row = requireOwned(accountId, session);
        SessionRecord next = new SessionRecord(
                row.id(),
                row.sessionId(),
                row.accountId(),
                row.workCode(),
                row.workId(),
                row.form(),
                row.stage(),
                row.schemaVersion(),
                row.contentVersion(),
                row.payloadJson(),
                row.checksum(),
                ENDED,
                row.version(),
                row.createdAt(),
                Instant.now());
        if (!store.updateSession(next, row.version())) {
            throw RuntimeFailure.conflict("session was updated on another device");
        }
        return store.findSession(row.sessionId()).orElse(next);
    }

    public static SessionStateSnapshot toSnapshot(SessionRecord row) {
        return new SessionStateSnapshot(
                new SessionRef(row.sessionId(), row.workCode()),
                row.stage(),
                row.schemaVersion(),
                row.payloadJson());
    }

    private SessionRecord requireSession(String sessionId) {
        return store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
    }

    private static void assertWork(SessionRecord row, String workCode) {
        if (!row.workCode().equals(workCode)) {
            throw RuntimeFailure.crossWork();
        }
    }

    static Applied applyMutation(SessionRecord row, StateMutation mutation) {
        String type = mutation.type().trim();
        String payload = mutation.payloadJson();
        String stage = row.stage();
        String nextJson = row.payloadJson();
        switch (type) {
            case "SET_STAGE" -> {
                stage = CanonicalJson.object(payload).path("stage").asText();
                if (stage.isBlank()) {
                    throw RuntimeFailure.badRequest("INVALID_MUTATION", "stage required");
                }
                nextJson = addReached(row.payloadJson(), stage);
            }
            case "PATCH" -> nextJson = CanonicalJson.mergePatch(row.payloadJson(), payload);
            case "REPLACE" -> {
                ObjectNode body = CanonicalJson.object(payload);
                JsonNode inner = body.has("payload") ? body.get("payload") : body;
                if (!inner.isObject()) {
                    throw RuntimeFailure.badRequest("INVALID_MUTATION", "payload must be object");
                }
                nextJson = CanonicalJson.write(inner);
            }
            case "TOOL_PATCH" -> nextJson = CanonicalJson.write(CanonicalJson.object(payload));
            case "END" -> {
                nextJson = CanonicalJson.mergePatch(row.payloadJson(), "{\"ended\":true}");
            }
            default -> throw RuntimeFailure.badRequest("INVALID_MUTATION", "unknown mutation type: " + type);
        }
        String checksum = CanonicalJson.checksum(row.schemaVersion(), row.contentVersion(), stage, nextJson);
        return new Applied(stage, nextJson, checksum);
    }

    static String addReached(String payloadJson, String stage) {
        ObjectNode payload = CanonicalJson.object(payloadJson);
        ArrayNode reached = payload.withArray("reached");
        boolean present = false;
        for (JsonNode item : reached) {
            if (stage.equals(item.asText())) {
                present = true;
                break;
            }
        }
        if (!present) {
            reached.add(stage);
        }
        return CanonicalJson.write(payload);
    }

    record Applied(String stage, String payloadJson, String checksum) {
    }
}

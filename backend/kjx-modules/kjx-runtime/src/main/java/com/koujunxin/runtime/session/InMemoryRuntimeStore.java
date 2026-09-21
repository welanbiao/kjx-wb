package com.koujunxin.runtime.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.koujunxin.runtime.sandbox.SandboxTicket;

public final class InMemoryRuntimeStore implements RuntimeStore {

    private final AtomicLong sessionSeq = new AtomicLong();
    private final AtomicLong saveSeq = new AtomicLong();
    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();
    private final Map<String, SaveRecord> saves = new ConcurrentHashMap<>();
    private final Map<String, SandboxTicket> tickets = new ConcurrentHashMap<>();

    @Override
    public void insertSession(SessionRecord row) {
        if (sessions.putIfAbsent(row.sessionId(), withId(row, sessionSeq.incrementAndGet())) != null) {
            throw new IllegalStateException("session exists");
        }
    }

    @Override
    public Optional<SessionRecord> findSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public List<SessionRecord> listSessions(long accountId, String workCode) {
        List<SessionRecord> out = new ArrayList<>();
        for (SessionRecord row : sessions.values()) {
            if (row.accountId() == accountId && row.workCode().equals(workCode)) {
                out.add(row);
            }
        }
        out.sort(Comparator.comparing(SessionRecord::updatedAt).reversed());
        return out;
    }

    @Override
    public boolean updateSession(SessionRecord row, int expectedVersion) {
        SessionRecord current = sessions.get(row.sessionId());
        if (current == null || current.version() != expectedVersion) {
            return false;
        }
        SessionRecord next = new SessionRecord(
                current.id(),
                row.sessionId(),
                current.accountId(),
                current.workCode(),
                current.workId(),
                current.form(),
                row.stage(),
                row.schemaVersion(),
                row.contentVersion(),
                row.payloadJson(),
                row.checksum(),
                row.status(),
                expectedVersion + 1,
                current.createdAt(),
                Instant.now());
        return sessions.replace(row.sessionId(), current, next);
    }

    @Override
    public void insertSave(SaveRecord row) {
        String key = saveKey(row.sessionId(), row.slot());
        if (saves.putIfAbsent(key, withSaveId(row, saveSeq.incrementAndGet())) != null) {
            throw new IllegalStateException("save exists");
        }
    }

    @Override
    public Optional<SaveRecord> findSave(String sessionId, String slot) {
        return Optional.ofNullable(saves.get(saveKey(sessionId, slot)));
    }

    @Override
    public List<SaveRecord> listSaves(long accountId, String workCode, String sessionId) {
        List<SaveRecord> out = new ArrayList<>();
        for (SaveRecord row : saves.values()) {
            if (row.accountId() == accountId
                    && row.workCode().equals(workCode)
                    && row.sessionId().equals(sessionId)) {
                out.add(row);
            }
        }
        out.sort(Comparator.comparing(SaveRecord::slot));
        return out;
    }

    @Override
    public boolean updateSave(SaveRecord row, int expectedVersion) {
        String key = saveKey(row.sessionId(), row.slot());
        SaveRecord current = saves.get(key);
        if (current == null || current.version() != expectedVersion) {
            return false;
        }
        SaveRecord next = new SaveRecord(
                current.id(),
                current.sessionId(),
                current.accountId(),
                current.workCode(),
                current.slot(),
                row.schemaVersion(),
                row.contentVersion(),
                row.stage(),
                row.stateJson(),
                row.checksum(),
                row.signature(),
                expectedVersion + 1,
                current.createdAt(),
                Instant.now());
        return saves.replace(key, current, next);
    }

    @Override
    public void insertTicket(SandboxTicket ticket) {
        tickets.put(ticket.nonceHash(), ticket);
    }

    @Override
    public Optional<SandboxTicket> findTicketByNonceHash(String nonceHash) {
        return Optional.ofNullable(tickets.get(nonceHash));
    }

    public void clear() {
        sessions.clear();
        saves.clear();
        tickets.clear();
    }

    private static String saveKey(String sessionId, String slot) {
        return sessionId + "\0" + slot;
    }

    private static SessionRecord withId(SessionRecord row, long id) {
        return new SessionRecord(
                id,
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
                row.status(),
                row.version(),
                row.createdAt(),
                row.updatedAt());
    }

    private static SaveRecord withSaveId(SaveRecord row, long id) {
        return new SaveRecord(
                id,
                row.sessionId(),
                row.accountId(),
                row.workCode(),
                row.slot(),
                row.schemaVersion(),
                row.contentVersion(),
                row.stage(),
                row.stateJson(),
                row.checksum(),
                row.signature(),
                row.version(),
                row.createdAt(),
                row.updatedAt());
    }
}

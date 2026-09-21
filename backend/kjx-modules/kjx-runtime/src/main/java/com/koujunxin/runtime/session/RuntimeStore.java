package com.koujunxin.runtime.session;

import java.util.List;
import java.util.Optional;

import com.koujunxin.runtime.sandbox.SandboxTicket;

public interface RuntimeStore {

    void insertSession(SessionRecord row);

    Optional<SessionRecord> findSession(String sessionId);

    List<SessionRecord> listSessions(long accountId, String workCode);

    boolean updateSession(SessionRecord row, int expectedVersion);

    void insertSave(SaveRecord row);

    Optional<SaveRecord> findSave(String sessionId, String slot);

    List<SaveRecord> listSaves(long accountId, String workCode, String sessionId);

    boolean updateSave(SaveRecord row, int expectedVersion);

    void insertTicket(SandboxTicket ticket);

    Optional<SandboxTicket> findTicketByNonceHash(String nonceHash);
}

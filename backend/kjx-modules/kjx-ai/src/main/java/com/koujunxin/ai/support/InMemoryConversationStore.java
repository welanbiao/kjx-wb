package com.koujunxin.ai.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.port.ConversationStore;
import com.koujunxin.ai.session.ChatMessage;
import com.koujunxin.ai.session.Conversation;
import com.koujunxin.ai.session.ConversationSummary;

public final class InMemoryConversationStore implements ConversationStore {

    private final Map<String, Conversation> sessions = new HashMap<>();
    private final Map<String, List<ChatMessage>> messages = new HashMap<>();
    private final Map<String, ConversationSummary> summaries = new HashMap<>();
    private final Map<String, AtomicInteger> seqs = new HashMap<>();

    @Override
    public synchronized Conversation open(long accountId, String sessionId, String workCode) {
        Conversation existing = sessions.get(sessionId);
        if (existing != null) {
            if (existing.accountId() != accountId) {
                throw AiException.rejected("session not owned");
            }
            if (!existing.workCode().equals(workCode)) {
                throw AiException.rejected("session work mismatch");
            }
            return existing;
        }
        Conversation created = new Conversation(sessionId, accountId, workCode, null, "ACTIVE", 0, null);
        sessions.put(sessionId, created);
        messages.put(sessionId, new ArrayList<>());
        seqs.put(sessionId, new AtomicInteger());
        return created;
    }

    @Override
    public synchronized Conversation requireOwned(long accountId, String sessionId) {
        Conversation existing = sessions.get(sessionId);
        if (existing == null) {
            throw AiException.rejected("session not found");
        }
        if (accountId > 0 && existing.accountId() != accountId) {
            throw AiException.rejected("session not owned");
        }
        return existing;
    }

    @Override
    public synchronized ChatMessage append(String sessionId, ChatMessage message) {
        Conversation conversation = sessions.get(sessionId);
        if (conversation == null) {
            throw AiException.rejected("session not found");
        }
        int seq = seqs.computeIfAbsent(sessionId, key -> new AtomicInteger()).incrementAndGet();
        ChatMessage stored = message.withSeq(seq);
        messages.computeIfAbsent(sessionId, key -> new ArrayList<>()).add(stored);
        sessions.put(sessionId, new Conversation(
                conversation.sessionId(),
                conversation.accountId(),
                conversation.workCode(),
                conversation.promptVersion(),
                conversation.status(),
                seq,
                conversation.lastTurnId()));
        return stored;
    }

    @Override
    public synchronized List<ChatMessage> recent(String sessionId, int limit) {
        List<ChatMessage> all = new ArrayList<>(messages.getOrDefault(sessionId, List.of()));
        all.sort(Comparator.comparingInt(ChatMessage::seq));
        if (all.size() <= limit) {
            return List.copyOf(all);
        }
        return List.copyOf(all.subList(all.size() - limit, all.size()));
    }

    @Override
    public synchronized List<ChatMessage> range(String sessionId, int fromSeqInclusive, int toSeqInclusive) {
        return messages.getOrDefault(sessionId, List.of()).stream()
                .filter(message -> message.seq() >= fromSeqInclusive && message.seq() <= toSeqInclusive)
                .sorted(Comparator.comparingInt(ChatMessage::seq))
                .toList();
    }

    @Override
    public synchronized Optional<ConversationSummary> latestSummary(String sessionId) {
        return Optional.ofNullable(summaries.get(sessionId));
    }

    @Override
    public synchronized ConversationSummary saveSummary(ConversationSummary summary) {
        summaries.put(summary.sessionId(), summary);
        return summary;
    }

    @Override
    public synchronized void touchTurn(String sessionId, String turnId, String promptVersion) {
        Conversation conversation = sessions.get(sessionId);
        if (conversation == null) {
            return;
        }
        sessions.put(sessionId, new Conversation(
                conversation.sessionId(),
                conversation.accountId(),
                conversation.workCode(),
                promptVersion,
                conversation.status(),
                conversation.messageCount(),
                turnId));
    }
}

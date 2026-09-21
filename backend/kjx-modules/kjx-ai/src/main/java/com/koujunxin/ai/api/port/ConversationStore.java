package com.koujunxin.ai.api.port;

import java.util.List;
import java.util.Optional;

import com.koujunxin.ai.session.ChatMessage;
import com.koujunxin.ai.session.Conversation;
import com.koujunxin.ai.session.ConversationSummary;

public interface ConversationStore {

    Conversation open(long accountId, String sessionId, String workCode);

    Conversation requireOwned(long accountId, String sessionId);

    ChatMessage append(String sessionId, ChatMessage message);

    List<ChatMessage> recent(String sessionId, int limit);

    List<ChatMessage> range(String sessionId, int fromSeqInclusive, int toSeqInclusive);

    Optional<ConversationSummary> latestSummary(String sessionId);

    ConversationSummary saveSummary(ConversationSummary summary);

    void touchTurn(String sessionId, String turnId, String promptVersion);
}

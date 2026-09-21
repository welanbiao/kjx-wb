package com.koujunxin.ai.session;

import java.util.List;
import java.util.Optional;

import com.koujunxin.ai.api.port.ConversationStore;

public final class SummaryPolicy {

    public static final int RECENT_TURNS = 10;
    public static final int SUMMARIZE_AFTER = 20;

    private final ConversationStore store;

    public SummaryPolicy(ConversationStore store) {
        this.store = store;
    }

    public void maybeSummarize(String sessionId, int messageCount) {
        if (messageCount < SUMMARIZE_AFTER) {
            return;
        }
        int until = store.latestSummary(sessionId).map(ConversationSummary::untilSeq).orElse(0);
        int keepFrom = Math.max(1, messageCount - RECENT_TURNS + 1);
        if (keepFrom - 1 <= until) {
            return;
        }
        List<ChatMessage> stale = store.range(sessionId, until + 1, keepFrom - 1);
        if (stale.isEmpty()) {
            return;
        }
        store.saveSummary(new ConversationSummary(sessionId, keepFrom - 1, compress(stale), "local"));
    }

    public List<ChatMessage> recentWindow(String sessionId) {
        return store.recent(sessionId, RECENT_TURNS);
    }

    public Optional<ConversationSummary> summary(String sessionId) {
        return store.latestSummary(sessionId);
    }

    static String compress(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            if (builder.length() > 1800) {
                break;
            }
            String content = message.content() == null ? "" : message.content().replace('\n', ' ').trim();
            if (content.length() > 80) {
                content = content.substring(0, 80);
            }
            if (!builder.isEmpty()) {
                builder.append(" | ");
            }
            builder.append(message.role()).append(':').append(content);
        }
        return builder.toString();
    }
}

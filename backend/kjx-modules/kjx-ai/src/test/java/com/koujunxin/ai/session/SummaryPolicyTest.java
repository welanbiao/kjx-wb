package com.koujunxin.ai.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.koujunxin.ai.support.InMemoryConversationStore;

class SummaryPolicyTest {

    @Test
    void compressesOlderMessagesAndKeepsRecentWindow() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        store.open(4L, "s", "breakup-editor");
        for (int i = 0; i < 25; i++) {
            store.append("s", ChatMessage.fromUser("s", "t", "内容" + i));
        }
        SummaryPolicy policy = new SummaryPolicy(store);
        policy.maybeSummarize("s", store.requireOwned(4L, "s").messageCount());
        assertTrue(policy.summary("s").isPresent());
        assertTrue(policy.summary("s").orElseThrow().untilSeq() >= 10);
        assertEquals(10, policy.recentWindow("s").size());
        assertEquals("内容24", policy.recentWindow("s").getLast().content());
    }
}

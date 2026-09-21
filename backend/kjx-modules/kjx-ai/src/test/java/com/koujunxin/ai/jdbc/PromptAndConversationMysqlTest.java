package com.koujunxin.ai.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.koujunxin.ai.prompt.PromptKind;
import com.koujunxin.ai.prompt.PromptRecord;
import com.koujunxin.ai.prompt.PromptStatus;
import com.koujunxin.ai.session.ChatMessage;
import com.koujunxin.ai.session.Conversation;
import com.koujunxin.ai.session.SummaryPolicy;

class PromptAndConversationMysqlTest extends MysqlAiSupport {

    @BeforeEach
    void clean() {
        resetTables();
    }

    @Test
    void flywayCreatesWp40SegmentOnStandardClasspath() {
        Integer versions = jdbc().queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('600','601','602','603')",
                Integer.class);
        assertEquals(4, versions);
        Integer charset = jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = DATABASE()
                   AND table_collation = 'utf8mb4_0900_ai_ci'
                   AND table_name IN ('kjx_prompt_template','kjx_ai_session','kjx_ai_message','kjx_ai_summary')
                """,
                Integer.class);
        assertEquals(4, charset);
    }

    @Test
    void workCatalogNeverReturnsMethodPrompt() {
        JdbcPromptCatalog catalog = prompts();
        catalog.saveMethod(new PromptRecord(
                "breakup-editor",
                PromptKind.METHOD,
                "SERVER",
                "0.1.0",
                PromptStatus.DRAFT,
                "METHOD_SECRET_BODY",
                "[]",
                "{}"));
        catalog.activateMethod("breakup-editor", "0.1.0");
        catalog.saveWork(new PromptRecord(
                "breakup-editor",
                PromptKind.WORK,
                "SERVER",
                "1.0.0",
                PromptStatus.DRAFT,
                "作品前缀",
                "[]",
                "{}"));
        catalog.activateWork("breakup-editor", "1.0.0");
        assertTrue(catalog.findActiveWork("breakup-editor").isPresent());
        assertEquals(PromptKind.WORK, catalog.findActiveWork("breakup-editor").orElseThrow().kind());
        assertFalse(catalog.findActiveWork("breakup-editor").orElseThrow().stablePrefix().contains("METHOD_SECRET"));
        Integer methods = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_prompt_template WHERE kind = 'METHOD' AND status = 'ACTIVE'",
                Integer.class);
        assertEquals(1, methods);
    }

    @Test
    void sessionMessagesAndSummaryPersist() {
        JdbcConversationStore store = conversations();
        Conversation conversation = store.open(3L, "sess-1", "breakup-editor");
        assertEquals(0, conversation.messageCount());
        for (int i = 0; i < 22; i++) {
            store.append("sess-1", ChatMessage.fromUser("sess-1", "t" + i, "消息" + i));
        }
        new SummaryPolicy(store).maybeSummarize("sess-1", store.requireOwned(3L, "sess-1").messageCount());
        assertTrue(store.latestSummary("sess-1").isPresent());
        assertEquals(10, store.recent("sess-1", 10).size());
        assertEquals("消息21", store.recent("sess-1", 10).getLast().content());
        store.touchTurn("sess-1", "turn-last", "1.0.0");
        assertEquals("turn-last", store.requireOwned(3L, "sess-1").lastTurnId());
    }
}

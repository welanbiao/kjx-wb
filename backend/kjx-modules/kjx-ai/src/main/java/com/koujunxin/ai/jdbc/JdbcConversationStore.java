package com.koujunxin.ai.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.port.ConversationStore;
import com.koujunxin.ai.openai.OpenAiToolCall;
import com.koujunxin.ai.session.ChatMessage;
import com.koujunxin.ai.session.Conversation;
import com.koujunxin.ai.session.ConversationSummary;

public final class JdbcConversationStore implements ConversationStore {

    private static final TypeReference<List<OpenAiToolCall>> TOOL_CALLS = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    public JdbcConversationStore(JdbcTemplate jdbc, TransactionTemplate tx, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.mapper = mapper;
    }

    @Override
    public Conversation open(long accountId, String sessionId, String workCode) {
        return tx.execute(status -> {
            List<Conversation> existing = jdbc.query(
                    """
                    SELECT session_id, account_id, work_code, prompt_version, status, message_count, last_turn_id
                      FROM kjx_ai_session WHERE session_id = ?
                    """,
                    (rs, rowNum) -> mapSession(rs),
                    sessionId);
            if (!existing.isEmpty()) {
                Conversation conversation = existing.getFirst();
                if (conversation.accountId() != accountId) {
                    throw AiException.rejected("session not owned");
                }
                if (!conversation.workCode().equals(workCode)) {
                    throw AiException.rejected("session work mismatch");
                }
                return conversation;
            }
            jdbc.update(
                    """
                    INSERT INTO kjx_ai_session (session_id, account_id, work_code, status, message_count)
                    VALUES (?, ?, ?, 'ACTIVE', 0)
                    """,
                    sessionId,
                    accountId,
                    workCode);
            return new Conversation(sessionId, accountId, workCode, null, "ACTIVE", 0, null);
        });
    }

    @Override
    public Conversation requireOwned(long accountId, String sessionId) {
        List<Conversation> existing = jdbc.query(
                """
                SELECT session_id, account_id, work_code, prompt_version, status, message_count, last_turn_id
                  FROM kjx_ai_session WHERE session_id = ?
                """,
                (rs, rowNum) -> mapSession(rs),
                sessionId);
        if (existing.isEmpty()) {
            throw AiException.rejected("session not found");
        }
        Conversation conversation = existing.getFirst();
        if (accountId > 0 && conversation.accountId() != accountId) {
            throw AiException.rejected("session not owned");
        }
        return conversation;
    }

    @Override
    public ChatMessage append(String sessionId, ChatMessage message) {
        return tx.execute(status -> {
            Integer count = jdbc.queryForObject(
                    "SELECT message_count FROM kjx_ai_session WHERE session_id = ? FOR UPDATE",
                    Integer.class,
                    sessionId);
            if (count == null) {
                throw AiException.rejected("session not found");
            }
            int seq = count + 1;
            jdbc.update(
                    """
                    INSERT INTO kjx_ai_message
                      (session_id, seq, turn_id, role, content, tool_call_id, tool_calls, token_count)
                    VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?)
                    """,
                    sessionId,
                    seq,
                    message.turnId(),
                    message.role(),
                    message.content(),
                    message.toolCallId(),
                    writeJson(message.toolCalls()),
                    message.tokenCount());
            jdbc.update(
                    "UPDATE kjx_ai_session SET message_count = ? WHERE session_id = ?",
                    seq,
                    sessionId);
            return message.withSeq(seq);
        });
    }

    @Override
    public List<ChatMessage> recent(String sessionId, int limit) {
        int safe = Math.max(1, limit);
        return jdbc.query(
                """
                SELECT session_id, seq, turn_id, role, content, tool_call_id, tool_calls, token_count, create_time
                  FROM kjx_ai_message
                 WHERE session_id = ?
                 ORDER BY seq DESC
                 LIMIT ?
                """,
                (rs, rowNum) -> mapMessage(rs),
                sessionId,
                safe).reversed();
    }

    @Override
    public List<ChatMessage> range(String sessionId, int fromSeqInclusive, int toSeqInclusive) {
        return jdbc.query(
                """
                SELECT session_id, seq, turn_id, role, content, tool_call_id, tool_calls, token_count, create_time
                  FROM kjx_ai_message
                 WHERE session_id = ? AND seq >= ? AND seq <= ?
                 ORDER BY seq ASC
                """,
                (rs, rowNum) -> mapMessage(rs),
                sessionId,
                fromSeqInclusive,
                toSeqInclusive);
    }

    @Override
    public Optional<ConversationSummary> latestSummary(String sessionId) {
        List<ConversationSummary> rows = jdbc.query(
                """
                SELECT session_id, until_seq, summary_text, model
                  FROM kjx_ai_summary
                 WHERE session_id = ?
                 ORDER BY until_seq DESC
                 LIMIT 1
                """,
                (rs, rowNum) -> new ConversationSummary(
                        rs.getString("session_id"),
                        rs.getInt("until_seq"),
                        rs.getString("summary_text"),
                        rs.getString("model")),
                sessionId);
        return rows.stream().findFirst();
    }

    @Override
    public ConversationSummary saveSummary(ConversationSummary summary) {
        jdbc.update(
                """
                INSERT INTO kjx_ai_summary (session_id, until_seq, summary_text, model)
                VALUES (?, ?, ?, ?)
                """,
                summary.sessionId(),
                summary.untilSeq(),
                summary.summaryText(),
                summary.model());
        return summary;
    }

    @Override
    public void touchTurn(String sessionId, String turnId, String promptVersion) {
        jdbc.update(
                "UPDATE kjx_ai_session SET last_turn_id = ?, prompt_version = ? WHERE session_id = ?",
                turnId,
                promptVersion,
                sessionId);
    }

    private ChatMessage mapMessage(ResultSet rs) throws SQLException {
        Instant created = rs.getTimestamp("create_time") == null
                ? Instant.now()
                : rs.getTimestamp("create_time").toInstant();
        return new ChatMessage(
                rs.getString("session_id"),
                rs.getInt("seq"),
                rs.getString("turn_id"),
                rs.getString("role"),
                rs.getString("content"),
                rs.getString("tool_call_id"),
                readCalls(rs.getString("tool_calls")),
                rs.getInt("token_count"),
                created);
    }

    private static Conversation mapSession(ResultSet rs) throws SQLException {
        return new Conversation(
                rs.getString("session_id"),
                rs.getLong("account_id"),
                rs.getString("work_code"),
                rs.getString("prompt_version"),
                rs.getString("status"),
                rs.getInt("message_count"),
                rs.getString("last_turn_id"));
    }

    private String writeJson(List<OpenAiToolCall> calls) {
        try {
            return mapper.writeValueAsString(calls == null ? List.of() : calls);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("tool_calls json", ex);
        }
    }

    private List<OpenAiToolCall> readCalls(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<OpenAiToolCall> calls = mapper.readValue(json, TOOL_CALLS);
            return calls == null ? List.of() : calls;
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}

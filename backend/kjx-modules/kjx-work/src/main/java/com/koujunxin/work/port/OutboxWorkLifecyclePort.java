package com.koujunxin.work.port;

import java.util.LinkedHashMap;
import java.util.Map;

import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxWriter;
import com.koujunxin.work.catalog.WorkRecord;

/**
 * 使用 WP-02 {@code OutboxWriter} 写入白名单事件 work.published / work.unpublished。
 */
public final class OutboxWorkLifecyclePort implements WorkLifecycleOutboxPort {

    private final OutboxWriter outboxWriter;

    public OutboxWorkLifecyclePort(OutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void onPublished(WorkRecord work, String reason, String actor) {
        append(work, "work.published", reason, actor);
    }

    @Override
    public void onUnpublished(WorkRecord work, String reason, String actor) {
        append(work, "work.unpublished", reason, actor);
    }

    private void append(WorkRecord work, String eventType, String reason, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", OutboxAppendCommand.CURRENT_SCHEMA);
        payload.put("workId", work.id());
        payload.put("workCode", work.workCode());
        payload.put("status", work.status().name());
        payload.put("reason", reason);
        payload.put("actor", actor);
        outboxWriter.append(OutboxAppendCommand.of("work", String.valueOf(work.id()), eventType, payload));
    }
}

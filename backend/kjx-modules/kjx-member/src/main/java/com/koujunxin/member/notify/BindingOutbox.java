package com.koujunxin.member.notify;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxWriter;

/**
 * 绑定/会员副作用只写 Outbox，禁止事务内直接发短信。
 */
public class BindingOutbox {

    private final OutboxWriter outbox;

    public BindingOutbox(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void bindingApproved(long bindingId, long accountId, long memberId, long deptId, String requestId, String idem) {
        appendDomain("binding", String.valueOf(bindingId), "binding.approved", Map.of(
                "schemaVersion", "1.0",
                "bindingId", bindingId,
                "accountId", accountId,
                "memberId", memberId,
                "deptId", deptId));
        appendNotification("BINDING_APPROVED", accountId, memberId, deptId,
                "会员绑定已通过", "会员绑定审核已通过。", requestId, idem);
    }

    public void bindingRejected(long bindingId, long accountId, long memberId, long deptId, String requestId, String idem) {
        appendDomain("binding", String.valueOf(bindingId), "binding.rejected", Map.of(
                "schemaVersion", "1.0",
                "bindingId", bindingId,
                "accountId", accountId,
                "memberId", memberId,
                "deptId", deptId));
        appendNotification("BINDING_REJECTED", accountId, memberId, deptId,
                "会员绑定未通过", "会员绑定审核未通过，如有疑问请提交申诉。", requestId, idem);
    }

    public void forceUnbound(long bindingId, long accountId, long memberId, long deptId, String requestId, String idem) {
        appendDomain("binding", String.valueOf(bindingId), "binding.force_unbound", Map.of(
                "schemaVersion", "1.0",
                "bindingId", bindingId,
                "accountId", accountId,
                "memberId", memberId,
                "deptId", deptId));
        appendNotification("BINDING_FORCE_UNBOUND", accountId, memberId, deptId,
                "会员已解绑", "账号与会员的绑定已解除。", requestId, idem);
    }

    public void memberUpdated(long memberId, long accountId, long deptId, String requestId, String idem) {
        appendDomain("member", String.valueOf(memberId), "member.updated", Map.of(
                "schemaVersion", "1.0",
                "memberId", memberId,
                "accountId", accountId,
                "deptId", deptId));
        appendNotification("MEMBER_UPDATED", accountId, memberId, deptId,
                "会员信息已更新", "后台已更新你的会员资料。", requestId, idem);
    }

    private void appendDomain(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        outbox.append(new OutboxAppendCommand(
                UUID.randomUUID().toString(),
                aggregateType,
                aggregateId,
                eventType,
                OutboxAppendCommand.CURRENT_SCHEMA,
                Instant.now(),
                null,
                null,
                null,
                payload));
    }

    private void appendNotification(String template, long accountId, long memberId, long deptId,
                                    String title, String content, String requestId, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("templateCode", template);
        payload.put("channels", List.of("INBOX", "SMS"));
        payload.put("accountId", accountId);
        payload.put("memberId", memberId);
        payload.put("deptId", deptId);
        payload.put("inboxType", "SECURITY");
        payload.put("title", title);
        payload.put("content", content);
        outbox.append(new OutboxAppendCommand(
                UUID.randomUUID().toString(),
                "notification",
                String.valueOf(accountId),
                "notification.requested",
                OutboxAppendCommand.CURRENT_SCHEMA,
                Instant.now(),
                null,
                requestId,
                idempotencyKey,
                payload));
    }
}

package com.koujunxin.account.notify;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.koujunxin.notification.outbox.OutboxAppendCommand;
import com.koujunxin.notification.outbox.OutboxWriter;

/**
 * 安全通知只写 Outbox，禁止直接调 SMS4J。
 */
public class SecurityOutbox {

    private final OutboxWriter outbox;

    public SecurityOutbox(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void accountRegistered(long accountId, String phoneMask, String requestId, String idempotencyKey) {
        appendDomain("account.registered", accountId, Map.of(
                "schemaVersion", "1.0",
                "accountId", accountId,
                "occurredAt", Instant.now().toString()));
        appendNotification("ACCOUNT_REGISTERED", accountId, phoneMask, "注册成功",
                "欢迎加入叩君芯。如非本人操作请忽略。", requestId, idempotencyKey);
    }

    public void securityLogin(long accountId, String phoneMask, String requestId) {
        appendNotification("SECURITY_LOGIN", accountId, phoneMask, "新设备登录",
                "检测到账号登录。如非本人操作请立即改密。", requestId, null);
    }

    public void passwordChanged(long accountId, String phoneMask, String requestId, String idempotencyKey) {
        appendDomain("account.password_changed", accountId, Map.of(
                "schemaVersion", "1.0",
                "accountId", accountId,
                "occurredAt", Instant.now().toString()));
        appendNotification("SECURITY_PASSWORD_CHANGED", accountId, phoneMask, "密码已修改",
                "账号密码已更新。如非本人操作请立即冻结账号。", requestId, idempotencyKey);
    }

    public void cancelScheduled(long accountId, String phoneMask, String requestId, String idempotencyKey) {
        appendDomain("account.cancel_scheduled", accountId, Map.of(
                "schemaVersion", "1.0",
                "accountId", accountId,
                "occurredAt", Instant.now().toString()));
        appendNotification("ACCOUNT_CANCEL_SCHEDULED", accountId, phoneMask, "账号注销已申请",
                "账号已进入 7 天冷静期，期间可撤销。冷静期内会员绑定保持不变。", requestId, idempotencyKey);
    }

    private void appendDomain(String eventType, long accountId, Map<String, Object> payload) {
        outbox.append(new OutboxAppendCommand(
                UUID.randomUUID().toString(),
                "account",
                String.valueOf(accountId),
                eventType,
                OutboxAppendCommand.CURRENT_SCHEMA,
                Instant.now(),
                null,
                null,
                null,
                payload));
    }

    private void appendNotification(String template, long accountId, String phoneMask,
                                    String title, String content, String requestId, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("templateCode", template);
        payload.put("channels", List.of("INBOX", "SMS"));
        payload.put("accountId", accountId);
        payload.put("inboxType", "SECURITY");
        payload.put("title", title);
        payload.put("content", content);
        if (phoneMask != null) {
            payload.put("phoneMask", phoneMask);
        }
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

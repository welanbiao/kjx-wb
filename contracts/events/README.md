# Outbox 事件编写规范（WP-02）

权威基线：`docs/13-统一架构DDL与接口契约基线.md`。冲突时以 13 为准。

## 必须遵守

1. 只通过 `OutboxWriter` 在**当前业务事务**内写入 `kjx_outbox_event`。禁止用 Spring `ApplicationEvent` 替代 Outbox。
2. Outbox 是**至少一次**投递。消费者按 `event_id` 幂等；通知再加 `channel`（`event_id + channel`）。外部 SMS 同样至少一次，向支持幂等的提供方传 `eventId`，不得声称 exactly-once。
3. 表列 `payload` 保存**完整信封**（对齐 `outbox-envelope.schema.json`），业务体嵌套在 `payload` 字段中，必须含 `schemaVersion`（当前 `"1.0"`）。写入前做白名单、UUID/长度/`occurredAt`/schemaVersion 与敏感字段递归校验；事件特定规则只校验嵌套 payload。
4. payload 必须脱敏：禁止明文手机号、密码、token、Authorization、API Key、验证码、Cookie。
5. 信封字段对齐 `outbox-envelope.schema.json`。`kjx_outbox_event` 表字段不得擅增 `locked_at` 等未冻结列；trace/request/idempotency 元数据留在 JSON 信封内。
6. Outbox 行 `PROCESSING` 租约使用 `next_retry_at`（到期即可被再次领取）。失败指数退避 + 抖动；超限 `DEAD`。
7. 消费回执表 `kjx_outbox_consume_receipt`（非冻结，见 ADR-001）：PK `(event_id, channel)`，`lease_until` 回收过期 PROCESSING，DONE 永不重复，abandon 可释放 PROCESSING。
8. `last_error` 只存脱敏截断摘要（≤500）。
9. `aggregateType` 只能取信封 enum：`account` / `member` / `binding` / `entitlement` / `unlock` / `work` / `notification` / `analytics`。
10. `eventType` 必须落在 `event-whitelist.json`。新增类型先改契约再写代码。

## payload 分发

| eventType | schema |
|---|---|
| `notification.requested` | `notification.schema.json` |
| `entitlement.granted` / `entitlement.revoked` | `entitlement.schema.json` |
| `unlock.recorded` | `unlock.schema.json` |
| `analytics.*.projected` | `analytics.schema.json` |
| 其他白名单类型 | 至少含 `schemaVersion`，且无敏感键 |

## 推荐写入步骤

1. 构造已脱敏的嵌套 payload（含 `schemaVersion`）。
2. 调用 `OutboxWriter.append`（模块内组装完整信封并做 UUID/长度/`occurredAt`/schemaVersion、白名单与敏感字段递归校验）。
3. 与 AUDIT、业务行同一事务提交。
4. 事务提交后由 Worker 领取，并把完整 envelope 交给 `OutboxPublisher`。
5. 发布成功再标 `PUBLISHED`。Outbox 行崩溃后靠 `next_retry_at` 回收；消费回执 PROCESSING 崩溃后靠 `lease_until` 回收。

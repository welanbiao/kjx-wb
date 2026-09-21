# Outbox 事件编写规范（WP-02）

权威基线：`docs/13-统一架构DDL与接口契约基线.md`。冲突时以 13 为准。

## 必须遵守

1. 只通过 `OutboxWriter` 在**当前业务事务**内写入 `kjx_outbox_event`。禁止用 Spring `ApplicationEvent` 替代 Outbox。
2. Outbox 是**至少一次**投递。消费者按 `event_id` 幂等；通知再加 `channel`（`event_id + channel`）。
3. `payload` **必须**包含 `schemaVersion`（当前 `"1.0"`），且写入前通过白名单与强类型校验。
4. payload 必须脱敏：禁止明文手机号、密码、token、Authorization、API Key、验证码、Cookie。
5. 信封字段对齐 `outbox-envelope.schema.json`。表字段不得擅增 `locked_at` 等未冻结列。
6. `PROCESSING` 租约使用 `next_retry_at`（到期即可被再次领取）。失败指数退避 + 抖动；超限 `DEAD`。
7. `last_error` 只存脱敏截断摘要（≤500）。
8. `aggregateType` 只能取信封 enum：`account` / `member` / `binding` / `entitlement` / `unlock` / `work` / `notification` / `analytics`。
9. `eventType` 必须落在 `event-whitelist.json`。新增类型先改契约再写代码。

## payload 分发

| eventType | schema |
|---|---|
| `notification.requested` | `notification.schema.json` |
| `entitlement.granted` / `entitlement.revoked` | `entitlement.schema.json` |
| `analytics.*.projected` | `analytics.schema.json` |
| 其他白名单类型 | 至少含 `schemaVersion`，且无敏感键 |

## 推荐写入步骤

1. 构造已脱敏 payload（含 `schemaVersion`）。
2. 调用 `OutboxWriter.append`（模块内会做白名单 / schemaVersion / 敏感字段校验）。
3. 与 AUDIT、业务行同一事务提交。
4. 事务提交后由 Worker 领取并调用 `OutboxPublisher`。
5. 发布成功再标 `PUBLISHED`。崩溃后靠 `next_retry_at` 回收。

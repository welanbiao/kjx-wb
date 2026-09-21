# ADR-001 Outbox 消费回执支持表

- 状态：Accepted
- 日期：2026-09-21
- 工作包：WP-02
- 影响：`kjx_outbox_consume_receipt`（V102）、`ConsumerIdempotencyPort`、docs/13 §3.5、docs/14 WP-02

## 背景

`kjx_outbox_event` 在 docs/13 §3.5 **冻结**：不得增删列（包括 `locked_at` / `trace_id` 等）。Outbox 投递是至少一次，通知消费者还要按 `event_id + channel` 去重。

若只用主键插入 PROCESSING、崩溃后不 complete，下一轮 `tryBegin` 会因主键冲突永久失败，本地站内信与外部 SMS 都会被卡住。

该回执表**不是**冻结表，V102 尚未发布，允许一次把租约与计数设计清楚。

## 决策

新增支持表 `kjx_outbox_consume_receipt`（Flyway V102，位于 `kjx-notification`）：

| 列 | 说明 |
|---|---|
| `event_id` + `channel` | 联合主键。channel 如 `INBOX` / `SMS` |
| `status` | `PROCESSING` / `DONE` |
| `attempt_count` | 领取与回收次数 |
| `lease_until` | PROCESSING 租约到期时刻 |
| `create_time` / `update_time` | 审计时间 |

语义：

1. **tryBegin 原子领取**：插入新 PROCESSING（写入 `lease_until`）；若主键已存在且为过期 PROCESSING，则 `UPDATE ... WHERE status='PROCESSING' AND lease_until<=now` 回收。租约未到期返回 false。
2. **DONE 永不重复**：`status='DONE'` 时 tryBegin 不得回收、不得改写。`abandon` 只删除 PROCESSING，不能解开 DONE。
3. **abandon 可释放**：处理失败删除 PROCESSING 行，后续 tryBegin 可重新插入。
4. **失败语义**：Outbox 投递与外部 SMS 都是**至少一次**。本地副作用（站内信）靠回执去重。向支持幂等的短信提供方必须传 `eventId` 作为提供方去重键。崩溃重投可能再次调用 SMS API，**不得声称 exactly-once**。
5. **不改冻结表**：envelope 元数据（`occurredAt` / `traceId` / `requestId` / `idempotencyKey`）写入 `kjx_outbox_event.payload` JSON（完整信封，业务体嵌套），不给 Outbox 表加列。

## 后果

- 消费者崩溃后，租约到期即可重试，不再永久阻塞。
- 并发下最多一个租约持有者执行本地副作用；外部通道仍可能重复调用，依赖提供方幂等。
- 集成方按 Flyway 标准 `classpath:db/migration` 扫描依赖 JAR 中的 V102；与 V101 一样由 WP-02 所有。

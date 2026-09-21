# WP-50 故障恢复说明

## 原则

- ClickHouse 停机**不得**影响账号、会员、解锁、账本、AUDIT/BIZ/BILLING 写入。
- 核心领域**不得**查询 ClickHouse。分析只允许最终一致。
- Outbox 是至少一次；ClickHouse 消费按 `eventId + channel` 幂等，重放不得产生业务重复。

## 故障模式

### 1. ClickHouse 不可达

现象：`AnalyticsProjectionPort` 仍成功（只写 MySQL Outbox）；Worker 投递失败，事件留在 `PENDING` 并指数退避，超过上限进入 `DEAD`。

业务侧：`AuditLogWritePort` / `BizLogWritePort` / `BillingLogWritePort` 与业务同事务，只要事务提交，MySQL 日志已在。

恢复：

1. 拉起 `deploy/observability` 中的 ClickHouse。
2. Worker 自动重试 `PENDING`。
3. 对 `DEAD`：`OutboxRepository.recoverDead(eventId)` 后 Worker 再 drain。
4. 消费者 `alreadyCompleted` 为 true 则跳过 INSERT；`ReplacingMergeTree` 按 `event_id` 去重。

### 2. 投影重复消费

`kjx_outbox_consume_receipt (event_id, channel)`：`DONE` 永不重复；租约过期的 `PROCESSING` 可 `tryBegin` 回收。外部副作用键为 `eventId` 本身，不声称 exactly-once。

### 3. 审计事务回滚

`AuditLogWritePort.append` 要求活跃 JDBC 事务。业务抛错时审计行与 Outbox 行一并回滚，不会留下半截哈希链。

### 4. Outbox 积压 / DEAD

指标：`kjx.outbox.backlog`、`kjx.outbox.dead`、`kjx.log.projection.errors`。告警见 `deploy/observability/prometheus/alerts.yml`。

处理：先看 `last_error`（已脱敏，≤500 字）→ 修 ClickHouse/网络 → `recoverDead` → drain → `ProjectionReconcilePort.reconcile`。

### 5. MDC 串请求

`MdcRequestContextFilter` 在 `finally` 中 `clear()` + `MDC.clear()`。请求结束后不得残留 `request_id` / `trace_id`。

## 对账

`log_projection_reconcile` 比较：

- MySQL Outbox `PUBLISHED` 数
- 消费回执 `DONE` 数
- ClickHouse 表行数

允许短暂 DRIFT；ClickHouse 不可用记 `CH_UNAVAILABLE`，不抛到核心 API。

## 演练清单

1. 停 ClickHouse → 解锁/审计事务仍成功，Outbox 积压。
2. 恢复 ClickHouse → drain → ACCESS 一行，再 drain 仍一行。
3. `recoverDead` 后重放成功。
4. `reconcile("ACCESS", start, end)` 为 MATCHED。

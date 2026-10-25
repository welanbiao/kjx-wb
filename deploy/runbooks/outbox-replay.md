# Outbox 积压、恢复与人工重放（WP-51）

`kjx_outbox_event` 是事务型 Outbox：**与业务同事务写入，提交后至少投递一次**。不承诺 exactly-once。重放安全的前提是消费者按 `eventId`（通知再加 `channel`）幂等，见 `docs/adr/ADR-001-outbox-consume-receipt.md`。

本手册不新增 Prometheus 规则。积压与 DEAD 告警已经写在：

- `deploy/observability/prometheus/alerts.yml`（`KjxOutboxBacklog`、`KjxOutboxBacklogStale`、`KjxOutboxDead`、`KjxOutboxPublishErrors`）
- 指标由 `kjx-server` 暴露（`management.endpoints.web.exposure.include` 含 `prometheus`，见 `deploy/observability/README.md`）

不要在本目录另写一份 alerts。观测栈本身是 `deploy/observability/docker-compose.yml`，与 M1 开发 Compose 分开。

本手册**未在 staging 做过重放演练**。

## 1. 投递语义

状态机（docs/13 §3.5，表字段冻结，禁止增删列）：

`PENDING → PROCESSING → PUBLISHED`，超过重试上限 → `DEAD`。

- Publisher / Worker（`kjx-notification` 的 `JdbcOutboxWorker`）领取 `PENDING`，租约写在已有列 `next_retry_at` 上，超时回收为 `PENDING`。
- 投递成功才标 `PUBLISHED`。
- 外部 SMS 同样至少一次；向支持幂等的提供方传 `eventId`，崩溃重投可能再次打到供应商。
- 本地副作用（站内信、ClickHouse 投影等）靠 `kjx_outbox_consume_receipt`：主键 `(event_id, channel)`；`DONE` 永不重复；过期 `PROCESSING` 可 `tryBegin` 回收；`abandon` 只释放 `PROCESSING`（ADR-001）。

因此：**人工重放 = 让失败行再次进入可领取队列，并依赖消费者幂等**。不是删数据，也不是改 `event_id`。

## 2. 告警怎么接现有规则

| 告警 | 文件中的表达式 | 含义 |
|---|---|---|
| `KjxOutboxBacklog` | `kjx_outbox_backlog > 100000` 持续 5m | 积压超过 10 万 |
| `KjxOutboxBacklogStale` | `kjx_outbox_backlog > 0` 持续 5m | 有积压且 5 分钟未消化完 |
| `KjxOutboxDead` | `kjx_outbox_dead > 0` 持续 1m | 出现 DEAD，需要人工 recover 后再投 |
| `KjxOutboxPublishErrors` | `increase(kjx_outbox_publish_errors_total[5m]) > 0` | 发布路径报错 |

处理顺序：看告警 → 只读查 `last_error`（已脱敏，≤500 字）→ **先修根因**（ClickHouse、短信 `stub`/`fail-closed`、网络）→ 再重放 DEAD。根因还在时重放只会再次 DEAD。

压测环境保持 `KJX_SMS_MODE=stub` 或 `fail-closed`，不要为了“把 Outbox 发干”去开真实短信。

## 3. 只读排查

在出问题的库上查询（密码走环境变量，不要写进命令历史文档）：

```sql
SELECT status, COUNT(*) AS n
  FROM kjx_outbox_event
 GROUP BY status;

SELECT event_id, event_type, aggregate_type, retry_count, next_retry_at, last_error, create_time
  FROM kjx_outbox_event
 WHERE status = 'DEAD'
 ORDER BY create_time
 LIMIT 100;

SELECT event_id, event_type, status, next_retry_at, retry_count
  FROM kjx_outbox_event
 WHERE status IN ('PENDING', 'PROCESSING')
 ORDER BY create_time
 LIMIT 100;
```

`PUBLISHED` 行表示业务侧已认为投递完成。即使下游还要靠回执去重，也**不要**把它们当失败事件处理。

对照回执（可选）：

```sql
SELECT status, COUNT(*) AS n
  FROM kjx_outbox_consume_receipt
 GROUP BY status;
```

`DONE` 表示该 `event_id + channel` 的本地副作用已完成；再次投递时消费者应跳过。

## 4. 人工重放：只把失败事件重新入队

仓库里没有单独的 HTTP「重放按钮」。实现入口是 `OutboxRepository.recoverDead(eventId)`：仅当 `status = 'DEAD'` 时把该行改回 `PENDING`、清空 `last_error`、重置 `retry_count`、把 `next_retry_at` 设为当前时间，随后 Worker 再 `claim` / `drain`。

**允许重放的条件（全部满足）：**

1. 该行当前是 `DEAD`，不是 `PUBLISHED` / `PROCESSING` / `PENDING`。
2. 已阅读 `last_error`，根因已消除或已改为可重试环境（例如观测栈 ClickHouse 已拉起，或压测改为 stub 短信）。
3. 消费者实现仍按 `eventId`（通知：`event_id + channel`）幂等；`DONE` 回执不会被本次重放解开。
4. 一次只处理已确认的 `event_id` 列表，不整表无差别更新。
5. 重放后观察 Worker 是否把该行推到 `PUBLISHED`，以及告警 `kjx_outbox_dead` 是否回落。

**不要做：**

- 删除 `PUBLISHED` 行，或任何「清成功事件好让积压好看」的语句。
- 删除 `kjx_outbox_consume_receipt` 里 `status = 'DONE'` 的回执来强迫重复站内信 / 重复投影。
- 修改 `event_id`、改写 `payload` 信封后再投（信封对齐 `OutboxEnvelope` / `outbox-envelope.schema.json`）。
- 声称重放之后外部 SMS 绝不会再收到同一 `eventId`。

`PENDING` / 租约过期的 `PROCESSING` 由 Worker 自动回收，一般**不必**人工入队。只有卡死在 `DEAD` 的才走 `recoverDead`。

若在紧急情况下必须用 SQL 代替调用 `recoverDead`，条件与实现相同：**WHERE status = 'DEAD'**，并且按 `event_id` 列出白名单。下面是与代码同语义的更新，**仍然不是删除**：

```sql
UPDATE kjx_outbox_event
   SET status = 'PENDING',
       retry_count = 0,
       last_error = NULL,
       next_retry_at = CURRENT_TIMESTAMP(3)
 WHERE event_id = '${EVENT_ID}'
   AND status = 'DEAD';
```

受影响行数必须为 1。为 0 说明该 id 不是 DEAD（可能已经是 PUBLISHED），停止操作。

## 5. 重放后核对

1. 该 `event_id` 变为 `PUBLISHED`（或短暂 `PROCESSING` 后变为 `PUBLISHED`）。
2. 对应 channel 的回执为 `DONE`；再 drain 一次行数不应增加。
3. 核心业务（账号、会员、解锁、账本、AUDIT/BIZ/BILLING）不得因为分析侧 ClickHouse 失败而回滚——ClickHouse 只是投影，见 `backend/kjx-modules/kjx-log/FAULT-RECOVERY.md`。
4. 不要用 ClickHouse 查询结果判断会员是否有效或解锁是否成功。

## 6. 与发布回滚的关系

回退 `kjx-server` 应用不会删除 Outbox 行。旧 Worker 与新 Worker 都必须遵守同一套至少一次 + 回执幂等。数据库回滚只能按 `deploy/runbooks/mysql-restore.md` 恢复到**新实例**，不能靠清空 `kjx_outbox_event` 冒充恢复。

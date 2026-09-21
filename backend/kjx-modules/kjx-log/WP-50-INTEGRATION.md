# WP-50 集成请求（根 POM / kjx-server / 默认 compose）

## 集成状态

主工程已完成 M1 接线：

- `kjx-server` 已配置结构化 MDC 日志、Prometheus actuator 与默认关闭的 ClickHouse 参数。
- `DomainIntegrationAutoConfiguration` 已将 member/work 审计端口委托到 `AuditLogWritePort`。
- Flyway 集成测试断言 V700–V708；test/it profile 强制关闭 ClickHouse。
- 可选 observability Compose 继续独立于默认 M1 Compose，且不再提供弱口令回退。
- ClickHouse 分析 Publisher 仍等待通知投递实现后由 composition root 组合；未组合前不得开启生产消费。

以下内容保留为模块边界、后续 Publisher 组合与运维约束。

## 1. 已由本模块完成、装配即可用

- 模块：`com.koujunxin:kjx-log`（`kjx-server` 已依赖该坐标）。
- Flyway：`classpath:db/migration` 下 V700–V708（`log_audit` / `log_biz` / `log_sec` / `log_billing` / `log_moderation` / `log_content_index` / `log_archive_manifest` / `log_retention_policy` / `log_projection_reconcile`）。
- Spring Boot 自动配置（显式 `after`）：
  - `LogAutoConfiguration`：脱敏、MDC、`RequestContextPort`、`TracePropagationPort`。无 DataSource 也启动。
  - `LogMysqlAutoConfiguration`：`@ConditionalOnBean(JdbcTemplate)` 才创建 AUDIT/BIZ/BILLING/SEC 写入端口。
  - `LogProjectionAutoConfiguration`：有 `OutboxWriter` 才创建分析投影入口；`kjx.log.clickhouse.enabled=true` 才创建 ClickHouse 客户端。
- 复用 WP-02 `OutboxEnvelope`、`OutboxWriter`、`ConsumerIdempotencyPort`、`eventId` 外部副作用键。**没有复制信封。**
- 可选观测栈：`deploy/observability/**`，工程名 `kjx-obs`。

## 2. Flyway

保持：

```yaml
spring.flyway.locations: classpath:db/migration
```

不要改成 `classpath*:`。V700–V708 与 WP-02 的 V101/V102 无冲突。集成测试应断言这些版本出现在 `flyway_schema_history`。

`log_billing` 是计费**日志信封**，不是 `ledger_entry`。账本事实表仍由计费工作包拥有。

## 3. `kjx-server` 配置（建议）

```yaml
kjx:
  log:
    redact-enabled: true
    clickhouse:
      enabled: false          # M1 默认必须 false
      url: http://127.0.0.1:8123
      username: default
      password: ${CLICKHOUSE_PASSWORD:}
      timeout-ms: 2000
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSSXXX, Asia/Shanghai} %-5level [%X{trace_id:-}] [%X{request_id:-}] %logger{36} - %msg%n"
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: kjx-server
```

- test profile **不要**打开 `kjx.log.clickhouse.enabled`。
- 打开 Prometheus 端点后，观测栈才能刮 `kjx.outbox.backlog` 等指标。可引入 `micrometer-registry-prometheus`（Boot BOM 已管理版本）。
- 可选 include：`deploy/observability/logging/logback-kjx-mdc.xml`。

## 4. 必须由 server / 业务模块接线的端口

业务在**当前事务**内调用，本模块不依赖 member/work/billing 实现：

```java
AuditLogWritePort.append(...)
BizLogWritePort.append(...)
BillingLogWritePort.append(...)
```

分析类 ACCESS/AI_CALL/TOOL_CALL：

```java
AnalyticsProjectionPort.projectAccess(...)  // 只写 Outbox
```

`WorkAuditPort` 目前仍是 WP-12 的 NoOp。请在 kjx-server 或 work 模块把 `WorkAuditPort` 委托给 `AuditLogWritePort`，**不要**让 work 直接依赖 ClickHouse。

## 5. OutboxPublisher 组合

本模块仅在 **没有** 其他 `OutboxPublisher` 且 ClickHouse 已启用时，注册分析投影 Publisher。通知投递合入后，请在 kjx-server 提供组合 Publisher：

```java
public void publish(OutboxEnvelope envelope) throws Exception {
    notificationPublisher.publish(envelope);
    clickHouseProjectionPublisher.publish(envelope); // 非 analytics 事件会 no-op
}
```

ClickHouse 失败只应让 Worker 重试 Outbox，不得回滚已提交的业务事务。

消费通道：`ch.access` / `ch.ai_call` / `ch.tool_call`，主键 `(event_id, channel)`，DONE 不重复。外部副作用去重键即 `eventId`（见 `ExternalProjectionKeys.clickHouseEventId`）。

## 6. 默认 compose

**禁止**把 ClickHouse/Higress/观测栈加入 `deploy/compose/docker-compose.dev.yml`。

可选启动：

```bash
docker compose --env-file .env -f deploy/observability/docker-compose.yml up -d
```

然后才允许 `kjx.log.clickhouse.enabled=true`。

## 7. 根 POM（可选，非阻塞）

当前 ClickHouse 访问走 JDK HttpClient，子模块未散落 clickhouse-jdbc 版本。若后续要官方 JDBC，请在根 `dependencyManagement` 登记后再让本模块引用，不要在子模块写 version。

可选登记（Boot BOM 已有 version 的不必再写）：

- `io.micrometer:micrometer-registry-prometheus`
- OpenTelemetry Java Agent（进程级，不必进 Maven）

## 8. 独立验证

```text
mvn -f backend/kjx-modules/pom.xml -pl kjx-log -am test
```

MySQL 8.4 + ClickHouse Testcontainers；`disabledWithoutDocker = true`。无 Docker 时跳过 IT，**禁止用 H2 替代**。

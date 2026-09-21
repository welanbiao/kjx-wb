# 可选观测栈（WP-50）

本目录独立于 M1 默认开发 Compose。**不要**把 ClickHouse / Higress / Loki / Tempo / Prometheus / Grafana 写进 `deploy/compose/docker-compose.dev.yml`。

默认 M1：

```bash
docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml up -d
```

可选观测（单独工程名 `kjx-obs`，端口只绑 127.0.0.1）：

```bash
docker compose --env-file .env -f deploy/observability/docker-compose.yml up -d
```

停止：

```bash
docker compose --env-file .env -f deploy/observability/docker-compose.yml down
```

## 服务

| 服务 | 端口 | 说明 |
|---|---|---|
| ClickHouse | 127.0.0.1:8123 | ACCESS / AI_CALL / TOOL_CALL 投影。核心业务不得查询 |
| OTel Collector | 127.0.0.1:4317/4318 | OTLP gRPC/HTTP |
| Prometheus | 127.0.0.1:9090 | 指标与告警规则 |
| Grafana | 127.0.0.1:3002 | 大盘（避开前台 3000） |
| Loki | 127.0.0.1:3100 | APP 日志 |
| Tempo | 127.0.0.1:3200 | TRACE |

Grafana 用户名缺省为 `admin`；Grafana 与 ClickHouse 密码必须在 `.env` 显式设置，
模板见仓库根 `.env.example`，Compose 不提供弱口令回退。

## 应用侧

```yaml
kjx:
  log:
    clickhouse:
      enabled: true
      url: http://127.0.0.1:8123
      username: default
      password: ${CLICKHOUSE_PASSWORD}
```

`kjx.log.clickhouse.enabled` 默认 false。ClickHouse 停机时分析事件留在 MySQL Outbox，AUDIT/BIZ/BILLING 事务不受影响。

建议用 OpenTelemetry Java Agent 把 APP/TRACE 打到 Collector，而不是在业务模块引入 SDK。

## 告警

`prometheus/alerts.yml`：Outbox 积压、DEAD、投影错误、5xx、AI 成本。
需要 `kjx-server` 暴露 `management.endpoints.web.exposure.include` 含 `prometheus`（见模块 `WP-50-INTEGRATION.md`）。

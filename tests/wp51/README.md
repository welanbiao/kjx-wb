# WP-51 测试与手册索引

本目录与 `tests/k6/`、`deploy/runbooks/` 补齐 WP-51 里**可离线审查**的发布/回滚/恢复说明和压测脚本草稿。  
**这些 k6 脚本未在本机执行压测，也未在 staging 跑过。** 跑通并记录结果之前，Gate 7 不能标绿。

`check-flyway-versions.ps1` 是静态扫描，不连数据库、不启动服务。

## 已有（不要当成 Gate 7 全部完成）

| 项 | 现状 |
|---|---|
| 空库迁移 | `backend/kjx-server` 的 `FlywayBaselineIT` 已覆盖空库迁到当前模块版本。本包不再复制一份 Java IT。升级库 / 跨版本滚动仍未单独交付。 |

## Gate 7 仍未覆盖

对照 `docs/14-多Agent并行开发实施方案.md` WP-51 与 Gate 7（安全、容量、故障、备份、恢复、回滚、staging 观察期）：

| 缺口 | 说明 |
|---|---|
| Gate A / B 横向越权 | Gate A：`DualDomainTokenIsolationTest`。探测表 Gate B：`DeptDataScopeIT`。业务表 Gate B：`BusinessDeptIsolationIT`。主播录入到审核的 HTTP 闭环：`MemberReviewHttpIT`（15 月 + 435 天，改月数不改额度）。绑定/解锁/账本的事务回滚已在各模块 Mysql 测试里。故障注入的进程崩溃演练、压测和恢复仍未跑。 |
| 绑定 / 解锁 / 账本故障注入 | 未交付可重复的故障注入实验（进程崩溃、Outbox 中断、账本写失败）。 |
| 100 SSE 长连接 | 真实模型 SSE **尚未挂到 OpenAPI**（`/v1/**` 为 M3，现行 `contracts/openapi/openapi.yaml` 不收录）。`tests/k6/sse-hold.js` 只用 `GET /actuator/health` 短连接占位，**不是** 100 路模型流验收，也**未执行**。 |
| 解锁并发与额度一致 | `tests/k6/unlock-concurrency.js` 只打 `POST /app/works/{workCode}/unlock`。验收「成功数 = 额度」必须另查库 / 对账，脚本本身不查库，且**未执行**。 |
| gitleaks / SCA / 安全头 / 依赖漏洞 | CI 与本目录均未接入扫描；无高危漏洞清单。 |
| MySQL 新实例恢复演练 | 手册在 `deploy/runbooks/mysql-restore.md`。未拉起新实例，未核对恢复后的 `flyway_schema_history`。备份成功≠恢复成功。 |
| Redis AOF / 对象存储版本 | 未写演练记录。 |
| Outbox 积压告警 + 人工重放演练 | 告警已在 `deploy/observability/prometheus/alerts.yml`，操作见 `deploy/runbooks/outbox-replay.md`。未在 staging 打出告警或重放 DEAD。 |
| staging 部署、回滚、约定观察期 | 手册在 `deploy/runbooks/release-rollback.md`。当前只有 `deploy/compose/docker-compose.dev.yml` + `kjx-server` 模块启动方式。**未部署 staging，未观察稳定期。** |
| OpenAPI 契约回归作为 Gate 7 签字 | 不在本包文件所有权内补 CI；此处不声称已回归。 |

## 本包新增文件

| 路径 | 用途 |
|---|---|
| `deploy/runbooks/release-rollback.md` | 发布前 Flyway 只向前；回滚回退 `kjx-server` 而不是删库；压测禁止真实短信/支付 |
| `deploy/runbooks/mysql-restore.md` | 全量 `mysqldump` + binlog 位点；恢复到新实例 |
| `deploy/runbooks/outbox-replay.md` | 至少一次投递、按 `eventId` 幂等重放、指向已有 Prometheus 告警 |
| `tests/k6/sse-hold.js` | 100 VU 短连接打 `/actuator/health`（需 `k6` 与 `BASE_URL`） |
| `tests/k6/unlock-concurrency.js` | 并发解锁（需 `BASE_URL`、`WORK_CODE`、`TOKEN`） |
| `tests/wp51/check-flyway-versions.ps1` | 扫描 `backend/**/db/migration/V*.sql` 的重复版本与版本段（忽略 Maven `target/` 拷贝） |

## 脚本如何跑（审查用，本机未压测）

Flyway 版本扫描（已在交付时执行一次，见完成报告）：

```powershell
powershell -NoProfile -File tests/wp51/check-flyway-versions.ps1
```

k6 **不要**对主机名含 `prod` / `production` 的地址跑。缺省 `BASE_URL` 会拒绝启动：

```bash
k6 run -e BASE_URL=http://127.0.0.1:8080 tests/k6/sse-hold.js
k6 run -e BASE_URL=http://127.0.0.1:8080 -e WORK_CODE=<workCode> -e TOKEN=<app-token> tests/k6/unlock-concurrency.js
```

压测环境：`KJX_SMS_MODE` 为 `stub` 或 `fail-closed`，禁止真实支付。详见 `deploy/runbooks/release-rollback.md`。

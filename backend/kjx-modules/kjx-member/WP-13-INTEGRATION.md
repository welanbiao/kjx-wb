# WP-13 集成请求（kjx-server / 总 OpenAPI / AUDIT / 作品装配顺序）

本工作包**没有**修改 `backend/pom.xml`、`backend/kjx-server/**`、`kjx-work/**`、`kjx-runtime-api/**`、`contracts/openapi/openapi.yaml`、`contracts/errors.yaml`。下列事项请由主 Agent / 集成 Agent 合入。

## 集成状态

主 Agent 已接线：总 OpenAPI 增加 `GET /admin-api/kjx/unlock-record/export`；HOST 可分页查询本部门解锁记录，导出与人工调整仍拒绝 HOST；`SEC_MISMATCH` 在同一请求里追加写入 `log_sec`。`EntitlementQueryPort` 由 Member 先于 Work 注册为 `JdbcEntitlementQueryPort`。

实现侧：

- Flyway：`V330__create_kjx_member_entitlement.sql`、`V331__create_kjx_unlock_record.sql`（`classpath:db/migration`，禁止 `classpath*:`）。
- `MemberAutoConfiguration` **先于** `WorkAutoConfiguration` 注册 `EntitlementQueryPort`，覆盖 WP-12 `EmptyEntitlementQueryPort`。
- `MemberUnlockAutoConfiguration` **晚于** `WorkAutoConfiguration`，注入 `WorkCatalogQueryPort` / `WorkVisibilityService` 后才创建解锁服务与 `EntitlementAccessPort`。
- 控制器：`AppUnlockController`、`AdminOpsUnlockController` 均 `@ConditionalOnBean(UnlockApplicationService.class)`。无 JDBC / Outbox / 作品端口时安全降级，骨架 profile 可启动。
- 新解锁只调用 `WorkVisibilityService.canNewUnlock`，不复制 CRC32 / 灰度公式。
- 已有 entitlement 在会员状态校验前短路，HTTP 200 / `code=1010`，不扣额度。
- Outbox 写完整信封：`entitlement.granted` / `entitlement.revoked` / `unlock.recorded`。事务内不外呼。
- AUDIT 复用 WP-11 `MemberAuditPort`（默认 `NoOpMemberAuditPort`，reason 必填）。

## 1. Flyway 扫描（必须）

| 版本 | 模块 | 脚本 |
|---|---|---|
| V330 | `kjx-member` | `db/migration/V330__create_kjx_member_entitlement.sql` |
| V331 | `kjx-member` | `db/migration/V331__create_kjx_unlock_record.sql` |

保持：

```yaml
spring.flyway.locations: classpath:db/migration
```

集成测试应断言 V330–V331 出现在 `flyway_schema_history`。表名按 docs/09 冻结为 `kjx_member_entitlement`（权益事实源）与 `kjx_unlock_record`。

在 docs/09 的 `uk_kjx_entitlement_member_work(member_id, work_id)` 之外，增加 `uk_kjx_entitlement_account_work(account_id, work_id)`，保证每个 account/work 也只有一条成功事实。`account_id` 可空（未绑定会员的官方加权益）；MySQL 唯一索引允许多个 NULL。

## 2. Spring 装配顺序（必须）

1. `MemberAutoConfiguration`：`beforeName = AccountAutoConfiguration, WorkAutoConfiguration`。
2. 必须先于 WP-12 注册：

```java
com.koujunxin.work.port.EntitlementQueryPort  // JdbcEntitlementQueryPort
```

3. `MemberUnlockAutoConfiguration`：`afterName = WorkAutoConfiguration`，再注册：

```java
com.koujunxin.runtime.api.port.EntitlementAccessPort  // JdbcEntitlementAccessPort
com.koujunxin.member.unlock.UnlockApplicationService
```

请确认 `kjx-server` 同时依赖 `kjx-member` 与 `kjx-work`，且**不要**再手动注册 `EmptyEntitlementQueryPort` 或第二套灰度实现。

`UnlockApplicationService` 还需要 `memberTransactionTemplate`、`OutboxWriter`、`IdempotencyService`、`WorkCatalogQueryPort`、`WorkVisibilityService`。

## 3. 必须由 kjx-server / WP-50 / WP-12 提供（禁止反向依赖）

### 3.1 AUDIT / SEC

生产请覆盖 `MemberAuditPort`，与解锁/人工调整**同一 JDBC 事务**写入 `log_audit`。

权益属于其他账号时本模块会：

- `log.warn("entitlement account mismatch accountId={} workId={} entitlementId={}")`（无手机号/token/会员编码）；
- `MemberAuditPort.record(..., "SEC_MISMATCH", "account mismatch", ...)`。

请主 Agent 将 `SEC_MISMATCH` 接到 WP-50 `log_sec`。模块内不能依赖 `kjx-log` 实现。

### 3.2 作品查询

生产使用 WP-12：

```java
com.koujunxin.work.api.WorkCatalogQueryPort
com.koujunxin.work.visibility.WorkVisibilityService
```

本模块不访问 work Mapper / `kjx_work` 表。现有端口足够完成解锁与列表 enrichment。可选增强（非阻塞）：批量 `findByIds`，减少「我的解锁」N+1。

### 3.3 账号注销态

新解锁读取 `AccountBindingCommandPort.find` 的 `cancelling()`。生产适配器需映射 `kjx_account.status`。

## 4. OpenAPI / 错误码

已改分片，**未改**总入口与 `errors.yaml`：

- `contracts/openapi/paths/app-work.yaml`（unlock / entitlements 语义）
- `contracts/openapi/paths/admin-ops.yaml`（人工权益 + 解锁记录分页；新增 `unlockRecordExport` 操作片段）
- `contracts/events/unlock.schema.json`（`unlock.recorded`）
- `contracts/events/README.md` 增加 payload 分发行

请在 `contracts/openapi/openapi.yaml` 增加：

```yaml
  /admin-api/kjx/unlock-record/export:
    $ref: "./paths/admin-ops.yaml#/unlockRecordExport"
```

错误码沿用冻结：1005/1006/1007/1008/1009/1010/1014/1090。已有权益返回 HTTP 200 + `code=1010`。

C 端：

- `POST /app/works/{workCode}/unlock`（`scene=unlock`，`ref_id=work_id`）
- `GET /app/entitlements`

运营（HOST 403）：

- `POST /admin-api/kjx/member/{id}/entitlement`
- `GET /admin-api/kjx/unlock-record/page`
- `GET /admin-api/kjx/unlock-record/export`（实现已有，总 OpenAPI 待接线）
- `POST /admin-api/kjx/member/{id}/recalc`（已有；现按 entitlement COUNT 重算 `unlocked_count`）

## 5. Maven 边界

`kjx-member` 现依赖 `kjx-work`（公开 api / visibility）与 `kjx-runtime-api`（`EntitlementAccessPort`）。不依赖 work 实现包。Enforcer `banCircularDependencies`：work 不得依赖 member。请勿让 `kjx-work` 反向依赖本模块。

## 6. 独立验证

```text
mvn -f backend/pom.xml -pl kjx-modules/kjx-member -am test
```

MySQL 8.4 + Redis 8 Testcontainers；`disabledWithoutDocker = true`。无 Docker 时跳过 IT，**禁止用 H2 替代**。

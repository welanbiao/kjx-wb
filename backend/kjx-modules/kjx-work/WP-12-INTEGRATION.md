# WP-12 聚合 / 依赖管理请求（集成 Agent）

本工作包**没有**修改 `backend/pom.xml`、`backend/kjx-modules/pom.xml`、`backend/kjx-server/**`、`contracts/openapi/openapi.yaml`、`contracts/errors.yaml`。下列事项请由集成 Agent 合入。

## 1. Flyway 扫描（必须）

WP-12 迁移在 `kjx-work` classpath：

| 版本 | 模块 | 脚本 |
|---|---|---|
| V400 | `kjx-work` | `db/migration/V400__create_kjx_work.sql` |
| V401 | `kjx-work` | `db/migration/V401__create_kjx_work_version.sql` |
| V402 | `kjx-work` | `db/migration/V402__create_kjx_content_version.sql` |

保持 Flyway 标准位置：

```yaml
spring.flyway.locations: classpath:db/migration
```

Flyway 会扫描依赖 JAR 中的同名 classpath 位置；不要使用 Flyway 不支持的 `classpath*:` 前缀。集成测试应断言 V400–V402 出现在 `flyway_schema_history`。

版本号 V400–V402 与 WP-02 的 V100–V102、WP-01 的 V1.x 不重叠。DDL 对齐 docs/09 §2.5，未增删冻结列。未加 `version` 乐观锁列：并发更新用 `id + status + update_time` CAS。

## 2. `kjx-server` 装配

`kjx-server` 已依赖 `kjx-work`，`scanBasePackages = com.koujunxin` 可扫到 `AppWorkController` / `AdminWorkController`。

请确认：

1. Spring Boot 加载 `WorkAutoConfiguration`（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）。
2. 存在 `JdbcTemplate`、`ObjectMapper`。
3. **不要**再注册第二套灰度/可见性实现。`WorkVisibilityService` 是唯一实现；列表、详情、WP-13 解锁必须注入它。
4. WP-13 提供 `EntitlementQueryPort` bean，覆盖默认 `EmptyEntitlementQueryPort`。禁止 work 模块读取 member Mapper。
5. 若存在 `OutboxWriter`，发布/下架会在同一事务写 `work.published` / `work.unpublished`（白名单已有，无需改 `errors.yaml` / 事件白名单）。
6. 管理写接口使用 WP-02 `IdempotencyService`：`scene=work_create`（`ref_id` 为 workCode 稳定哈希）、`scene=work_update`（`ref_id=work_id`）。这两个 scene 不是冻结三场景的改写，只是同一张 `kjx_idempotency_guard` 表。
7. 作品为平台目录，**无 `dept_id`**，不走 HOST `@DataPermission`。HOST 数据权限仍只作用于 member/binding/entitlement/unlock-record。

## 3. OpenAPI / 错误码

- 已改分片：`contracts/openapi/schemas/work.yaml`、`contracts/openapi/paths/app-work.yaml`（仅文档语义，未改路径/字段/错误码）。
- **未改**总入口 `contracts/openapi/openapi.yaml`（路径已指向上述分片）。
- **未改** `contracts/errors.yaml`。C 端下架无权益详情返回 **1008 WORK_UNPUBLISHED**（HTTP 403）。
- 建议（可选，未改 `admin-ops.yaml`）：`PUT /admin-api/kjx/work/{id}` 增加 HTTP 409，用于乐观锁冲突。当前实现已返回 409 / `WORK_UPDATE_CONFLICT`。
- **未实现** `POST /app/works/{workCode}/unlock` 与 `GET /app/entitlements`（WP-13）。

## 4. 给 WP-13 / WP-20 的端口

```java
com.koujunxin.work.visibility.WorkVisibilityService  // 唯一可见性
com.koujunxin.work.api.WorkCatalogQueryPort
com.koujunxin.work.query.PublicWorkQueryService#canAccessUnpublishedIfEntitled
com.koujunxin.work.port.EntitlementQueryPort          // WP-13 实现
com.koujunxin.work.support.InMemoryEntitlementQueryPort // 测试桩
```

新解锁：`visibility.canNewUnlock(work, accountId)` 为 false 时返回 1008。已有 entitlement 必须在此之前短路 1010。

## 5. 独立验证

```text
mvn -f backend/pom.xml -pl kjx-modules/kjx-work -am test
```

Testcontainers MySQL 8.4；`disabledWithoutDocker = true`，无 Docker 时跳过 IT，**禁止用 H2 替代**。

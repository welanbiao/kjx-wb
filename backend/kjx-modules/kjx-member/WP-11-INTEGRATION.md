# WP-11 集成请求（根 POM / kjx-server / 总 OpenAPI / errors.yaml）

## 集成状态

主工程已完成接线：

- `DomainIntegrationAutoConfiguration` 在 `MemberAutoConfiguration` 前注册账号绑定、主播部门、
  RuoYi 数据权限和审计适配器。
- 主播创建会在同一事务创建 `sys_dept`、迁移指定 `sys_user.dept_id` 并授予 `HOST` 角色。
- `AdminSelfMemberController` 显式要求纯 HOST 身份，运营账号不能复用自助路径。
- Flyway 集成测试断言 V300–V303，并验证生产占位端口均被 composition root 覆盖。

以下内容保留为模块边界与运维约束。

## 1. Flyway 扫描（必须）

WP-11 迁移在 `kjx-member` classpath：

| 版本 | 模块 | 脚本 |
|---|---|---|
| V300 | `kjx-member` | `db/migration/V300__create_kjx_host.sql` |
| V301 | `kjx-member` | `db/migration/V301__create_kjx_member.sql` |
| V302 | `kjx-member` | `db/migration/V302__create_kjx_member_binding.sql` |
| V303 | `kjx-member` | `db/migration/V303__create_kjx_binding_appeal.sql` |

保持 Flyway 标准位置：

```yaml
spring.flyway.locations: classpath:db/migration
```

Flyway 会扫描依赖 JAR 中的同名 classpath 位置；**不要**使用 Flyway 不支持的 `classpath*:` 前缀。集成测试应断言 V300–V303 出现在 `flyway_schema_history`。

`kjx-server` 已依赖 `kjx-member` 时无需改坐标。请同时保证 WP-02 的 V100–V102、WP-10 的 V200–V202 先于本段执行（版本号不重叠）。

## 2. Spring 装配顺序（必须）

`MemberAutoConfiguration` 声明：

- `after = JdbcTemplateAutoConfiguration / IdempotencyAutoConfiguration / OutboxAutoConfiguration`
- `beforeName = com.koujunxin.account.config.AccountAutoConfiguration`

必须 **先于** WP-10 `AccountAutoConfiguration` 注册 `BoundMemberQueryPort`，否则账号模块会先放入 empty 默认实现，登录 `member` 恒为 null。

控制器均 `@ConditionalOnBean(MemberApplicationService.class)`。无 `JdbcTemplate` / `OutboxWriter` / `IdempotencyService` 的骨架 profile 下不会创建应用服务，上下文可启动。

`MemberApplicationService` 还需要 `PlatformTransactionManager`（通过 `memberTransactionTemplate`）。

## 3. 必须由 kjx-server / WP-10 提供的端口（禁止反向依赖）

本模块**不得**访问 account Mapper，也**不得**对 `kjx_account` 发生产 SQL。已定义：

```java
com.koujunxin.member.api.port.AccountBindingCommandPort
```

请在 `kjx-server`（或 kjx-account 的公开实现类）提供 bean，内部使用 WP-10 `AccountRepository` / JDBC 更新：

| 方法 | 对应列 |
|---|---|
| `find` | `status`, `bind_status`, `bind_fail_count`, `bind_locked_until` |
| `updateBindStatus` | `bind_status`：0 未绑定 / 1 审核中 / 2 已绑定 / 3 已驳回 / 4 解绑中 |
| `recordFailure` | 失败计数；满 3 次写 `bind_locked_until = now+30min` |
| `resetFailures` | 提交待审成功后清零 |

默认 `MissingAccountBindingCommandPort` 仅内存占位，生产必须覆盖。

`kjx-account` **不要**依赖 `kjx-member`。`BoundMemberQueryPort` 继续留在 account-api，由本模块实现。

## 4. 部门权限与 sys_dept

默认 `LoginHelperDeptAccessPort`：超管放行，否则仅本部门。运营「全部数据」请用 WP-01 `DeptDataScopeGuard` 覆盖 `DeptAccessPort` bean。

`kjx_host.dept_id = sys_dept.id`。本模块不写系统 Mapper。已定义：

```java
com.koujunxin.member.api.port.HostDeptPort
```

生产实现应：创建部门（`hostName` + 可选 `parentDeptId`）、改名、按 deptId 查 `dept_name`。未覆盖时使用内存实现，仅便于模块测试。

`CreateHostRequest.adminUserId` 关联 `sys_user`：请在适配器中把该用户 `dept_id` 指到新建部门，并授 HOST 角色。本包未改 `sys_user`。

AUDIT 默认 `NoOpMemberAuditPort`（仍强制 reason 非空）。请接入 `log_audit`，与绑定审核同事务。

## 5. 通知 / 幂等

审核通过、驳回、强制解绑只调 WP-02 `OutboxWriter`：

- 领域事件：`binding.approved` / `binding.rejected` / `binding.force_unbound` / `member.updated`
- 通知模板：`BINDING_APPROVED` / `BINDING_REJECTED` / `BINDING_FORCE_UNBOUND` / `MEMBER_UPDATED`，channels `INBOX+SMS`

事务内禁止 SMS4J。C 端绑定幂等 `scene=binding`、`ref_id=account_id`（冻结场景）。其它写接口使用同表扩展 scene（`member_create` / `binding_approve` 等），不是改冻结三场景。

## 6. OpenAPI / 错误码

已使用既有分片，**未改**总入口与 `errors.yaml`：

- `contracts/openapi/paths/app-member.yaml`
- `contracts/openapi/paths/admin-self.yaml`
- `contracts/openapi/paths/admin-ops.yaml`（本 WP 只实现 member/binding/appeal/host；`entitlement` / `unlock-record` / `work` 仍属 WP-13/12）
- `contracts/openapi/schemas/member.yaml`

错误码沿用 1001/1002/1003/1004/1014/1090。

## 7. 权限矩阵（后台）

| 路径 | HOST 自助 | 运营 |
|---|---|---|
| `/admin-api/kjx/self/member/**` | 仅本 `dept_id` | 不走此路径 |
| `/admin-api/kjx/self/binding/page` | 仅本部门只读 | 不走此路径 |
| `/admin-api/kjx/member/**` | 403 | 按数据权限 |
| `/admin-api/kjx/binding/**` 审核/驳回/强制解绑 | 403 | 按数据权限 |
| `/admin-api/kjx/appeal/**` | 403 | 运营 |
| `/admin-api/kjx/host/**` | 仅 `GET /self/host` | CRUD |

按 ID 读写在 Service 内 `DeptAccessPort.requireAccess(resource.dept_id)`，不只依赖列表 SQL。

## 8. 独立验证

```text
mvn -f backend/pom.xml -pl kjx-modules/kjx-member -am test
```

MySQL 8.4 + Redis 8 Testcontainers；`disabledWithoutDocker = true`。无 Docker 时跳过 IT，**禁止用 H2 替代**。

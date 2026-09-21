# 叩君芯会员产品平台 · 统一架构、DDL 与接口契约基线
## Unified Architecture, DDL & API Contract Baseline v1.0

> **状态**：可实施 ｜ **用途**：所有开发 Agent 的唯一技术入口  
> **适用范围**：M1～M6  
> **最后同步**：2026-09-21

---

## 0. 文档优先级

发生冲突时按以下顺序执行：

1. **本文**：架构边界、DDL 字段语义、API 路径、事务、幂等、事件。
2. `11-M1接口契约与业务联动.md`：M1/M2 请求响应细节。
3. `09-M1数据库设计与项目骨架.md`：M1/M2 完整建表语句。
4. `08-系统架构设计.md`：技术栈、部署与模块职责。
5. `04-完整功能规格说明书.md`：产品规则。
6. `05`、`06`、`07`、`10`、`12`：功能清单、日志、决策、成本、执行清单。

历史示例不得覆盖本文。需要改变本文时，先新增 ADR 并同步受影响文档、OpenAPI、DDL 和测试。

---

## 1. 已冻结决策

### 1.1 系统形态

- 单仓库，目录使用 `backend/`、`apps/web/`、`apps/admin/`、`contracts/`、`deploy/`。
- 后端是单一 `kjx-server` Spring Boot 进程；业务模块是 Maven jar。
- 不拆微服务，不上 K8s，不做分库分表。
- Nginx 负责 TLS/静态/反代；Higress 从 M3 起负责 AI 路由、配额、SSE 与限流。
- M1 只启动 MySQL、Redis、Nginx、后端、前后台；ClickHouse/Higress/观测栈按里程碑引入。

### 1.2 数据职责

- **MySQL 8.4**：账号、会员、绑定、权益、作品、订单、账本、审计、Outbox 等业务事实。
- **Redis 8**：会话、验证码、缓存、限流、锁、短期计数、延迟任务；不得作为永久业务事实源。
- **ClickHouse**：`ACCESS`、`AI_CALL`、`TOOL_CALL` 分析投影；不得用于权限、权益、余额或状态判断。
- **对象存储**：作品包、素材、音乐、存档大对象、加密内容日志和归档。

### 1.3 身份与权限

- C 端账号：`kjx_account`，Sa-Token `loginType=app`。
- 后台账号：RuoYi `sys_user`，Sa-Token `loginType=admin`。
- 主播 = `sys_dept` 部门；`kjx_host.dept_id = sys_dept.id`。
- `kjx_member_binding`、`kjx_member_entitlement`、`kjx_unlock_record` 必须冗余 `dept_id`。
- 所有 HOST 的列表、详情、修改、审核、导出都必须在后端执行数据权限检查。

### 1.4 会员与权益

- `subscribed_months`：运营从抖音后台直接抄录，仅用于绑定三要素。
- `subscribed_days`：累计已订阅天数，仅用于额度计算。
- 两字段均为必填，互不换算；禁止 `months * 30` 或 `days / 30` 推导。
- `available_count = floor(subscribed_days / 30)`。
- 新解锁要求会员 `VALID`；已有 entitlement 是永久买断权益。
- 访问已解锁作品、会话、存档、Tool 不再检查会员是否 `VALID`。

### 1.5 一致性与消息

- 绑定审核、解锁、人工权益调整、账本和关键 AUDIT 使用同一个 MySQL 事务。
- 通知、ClickHouse 投影、统计等副作用使用 Transactional Outbox。
- Outbox 是至少一次投递；所有消费者必须按 `event_id` 或业务唯一键幂等。
- Spring `ApplicationEvent` 只能唤醒投递器或处理可丢失的进程内通知。

### 1.6 时间

- MySQL、JVM、容器统一 `Asia/Shanghai`。
- `expire_at` 存“最后有效时刻”，精确到毫秒。
- 若运营只输入日期，服务端展开为当天 `23:59:59.999`。
- 有效判断：`start_at <= now AND now <= expire_at`。

---

## 2. 模块与依赖基线

```text
account-api     work-api       runtime-api         billing-api
    ▲              ▲              ▲                    ▲
    │              │              │                    │
account        member ───────► entitlement             billing
                   │              ▲                    ▲
                   └────► work ───┼────► runtime ──────┤
                                  │         ▲          │
                                  └──── tool ├──── ai ─┴── channel
```

### 2.1 强制规则

1. 业务模块只能依赖目标模块公开的 `api`/`port` 包。
2. 禁止跨模块访问 Mapper、Entity、Repository 实现和内部 Service。
3. `kjx-runtime` 与 `kjx-tool` 均依赖 `kjx-runtime-api`，不得相互依赖。
4. `kjx-server` 只负责装配、配置与启动，不写领域业务。
5. 使用 ArchUnit/Maven Enforcer 检查：
   - 无循环依赖；
   - controller 不直接访问 mapper；
   - ClickHouse client 不得出现在核心领域模块；
   - runtime/tool 不得 import 对方实现包。

### 2.2 runtime-api 最小端口

```java
public interface SessionStateQueryPort {
    SessionStateSnapshot load(SessionRef session);
}

public interface SessionStateCommandPort {
    SessionStateSnapshot apply(SessionRef session, StateMutation mutation);
}

public interface SessionAuthorizationPort {
    void requireOwner(long accountId, SessionRef session);
}

public interface EntitlementAccessPort {
    void requireEntitlement(long accountId, String workCode);
}

public interface ToolExecutionPort {
    ToolResult execute(ToolExecutionCommand command);
}
```

端口 DTO 不得引用 runtime/tool 的 Entity。

---

## 3. DDL 基线

### 3.1 通用约定

- InnoDB、`utf8mb4_0900_ai_ci`、`DATETIME(3)`。
- 主键 `BIGINT UNSIGNED AUTO_INCREMENT`；事件 ID 可使用 UUID。
- MySQL 业务表在 M1～M6 不分区。
- 账本、审计、解锁记录不逻辑删除。
- Flyway 是唯一 Schema 入口；禁止手工导入后再重复跑 baseline。
- 每个迁移只完成一个可描述的 Schema 变更。

### 3.2 Flyway 版本段

- `V100–V149`：框架、幂等、Outbox。
- `V200–V249`：账号。
- `V300–V349`：会员、绑定、权益。
- `V400–V449`：作品、内容、素材。
- `V500–V549`：通道、计费、订单、支付。
- `V600–V649`：AI、Tool、Runtime。
- `V700–V749`：日志与分析投影。

版本段由对应工作包独占；跨段迁移交给集成 Agent。

### 3.3 权限冗余字段

以下三表必须包含：

```sql
`dept_id` BIGINT UNSIGNED NOT NULL COMMENT '主播部门快照'
```

推荐索引：

```sql
CREATE INDEX idx_binding_dept_status
  ON kjx_member_binding(dept_id, status, create_time);

CREATE INDEX idx_entitlement_dept_member
  ON kjx_member_entitlement(dept_id, member_id, work_id);

CREATE INDEX idx_unlock_dept_member
  ON kjx_unlock_record(dept_id, member_id, unlock_time);
```

写入时从 `kjx_member.dept_id` 复制；之后不得跟随部门变更静默改历史快照。

### 3.4 幂等守护

```sql
CREATE TABLE `kjx_idempotency_guard` (
  `scene`           VARCHAR(32)     NOT NULL,
  `ref_id`          BIGINT UNSIGNED NOT NULL,
  `idempotency_key` VARCHAR(64)     NOT NULL,
  `actor_id`        BIGINT UNSIGNED DEFAULT NULL,
  `request_hash`    CHAR(64)        NOT NULL,
  `status`          VARCHAR(16)     NOT NULL DEFAULT 'PROCESSING',
  `result_code`     VARCHAR(32)     DEFAULT NULL,
  `result_body`     JSON            DEFAULT NULL,
  `expire_at`       DATETIME(3)     NOT NULL,
  `create_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`scene`, `ref_id`, `idempotency_key`),
  KEY `idx_idem_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

语义：

- binding：`scene=binding`、`ref_id=account_id`。
- unlock：`scene=unlock`、`ref_id=work_id`。
- pay callback：`scene=pay_callback`、`ref_id=payment_id`。
- 相同主键、相同 `request_hash`：返回首次结果。
- 相同主键、不同 `request_hash`：HTTP 409，业务码 `IDEMPOTENCY_CONFLICT`。
- guard 与业务写入同事务；失败回滚后不得留下成功占位。

### 3.5 Transactional Outbox

```sql
CREATE TABLE `kjx_outbox_event` (
  `event_id`        CHAR(36)       NOT NULL,
  `aggregate_type`  VARCHAR(32)    NOT NULL,
  `aggregate_id`    VARCHAR(64)    NOT NULL,
  `event_type`      VARCHAR(64)    NOT NULL,
  `payload`         JSON           NOT NULL,
  `status`          VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
  `retry_count`     INT UNSIGNED   NOT NULL DEFAULT 0,
  `next_retry_at`   DATETIME(3)    DEFAULT NULL,
  `last_error`      VARCHAR(500)   DEFAULT NULL,
  `create_time`     DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `published_at`    DATETIME(3)    DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  KEY `idx_outbox_poll` (`status`, `next_retry_at`, `create_time`),
  KEY `idx_outbox_aggregate` (`aggregate_type`, `aggregate_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

状态：`PENDING → PROCESSING → PUBLISHED`；超过重试上限进入 `DEAD`。

处理要求：

- 使用数据库抢占或 `SELECT ... FOR UPDATE SKIP LOCKED` 批量领取。
- `PROCESSING` 超时可回收。
- 指数退避并加入抖动。
- `last_error` 只存脱敏摘要。
- 消费成功后再标记 `PUBLISHED`。

### 3.6 账号设备

- 禁止存储明文 Sa-Token。
- `kjx_account_device` 只存 `token_hash` 或可撤销的 session id。

### 3.7 完整表清单

M1/M2 的完整 DDL 见 `09`，但必须包含上述覆盖项。后续模块表由对应工作包在预留版本段内补充。

---

## 4. API 通用契约

### 4.1 路径

- `/app/auth/**`：C 端匿名认证接口。
- `/app/**`：C 端登录接口，`loginType=app`。
- `/admin-api/kjx/self/**`：HOST 自助接口，`loginType=admin` + 部门权限。
- `/admin-api/kjx/**`：运营接口，`loginType=admin` + 权限点。
- `/v1/**`：OpenAI 兼容代理，M3 起启用。

旧 `/api/**` 路径全部废止，不提供双轨。

### 4.2 响应与时间

```json
{
  "code": 0,
  "data": {},
  "msg": "",
  "requestId": "01J..."
}
```

- HTTP 状态表达协议结果，`code` 表达业务结果。
- 时间统一 ISO-8601，带 `+08:00`。
- 分页统一 `pageNo`、`pageSize`，响应 `list`、`total`。

### 4.3 请求头

- `Authorization: Bearer {token}`
- `X-Idempotency-Key`：所有有外部副作用的 POST 必填。
- `X-Device-Id`
- `X-Client-Version`
- `X-Request-Id`：可选；服务端缺失时生成。

### 4.4 核心错误码

- `1001 BINDING_MISMATCH`
- `1002 BINDING_TAKEN`
- `1003 ACCOUNT_ALREADY_BOUND`
- `1004 BINDING_LOCKED`
- `1005 MEMBER_NOT_STARTED`
- `1006 MEMBER_EXPIRED`
- `1007 UNLOCK_QUOTA_EXHAUSTED`
- `1008 WORK_UNPUBLISHED`
- `1009 BINDING_REQUIRED`
- `1010 ALREADY_UNLOCKED`
- `1011 VERIFY_CODE_INVALID`
- `1012 RATE_LIMITED`
- `1013 ACCOUNT_LOCKED`
- `1014 ACCOUNT_CANCELLING`
- `1090 IDEMPOTENCY_CONFLICT`

错误码常量只允许在 `contracts/errors.yaml` 定义一次。

---

## 5. M1/M2 API 冻结清单

### 5.1 账号

- `POST /app/auth/send-code`
- `POST /app/auth/register`
- `POST /app/auth/login`
- `POST /app/auth/login-by-code`
- `POST /app/auth/logout`
- `POST /app/auth/refresh`
- `POST /app/auth/reset-password`
- `GET|PUT /app/account/profile`
- `PUT /app/account/password`
- `GET /app/account/devices`
- `DELETE /app/account/devices/{id}`
- `POST /app/account/cancel`

注册仅支持手机号。

### 5.2 会员绑定

`POST /app/member/binding`

```json
{
  "hostCode": "KJX",
  "douyinId": "zhangsan",
  "memberCode": "KJX-A3F9K2B7",
  "claimMonths": 15
}
```

- `scene=binding`、`ref_id=account_id`。
- 错误三要素统一返回 `1001`。

### 5.3 主播录会员

`POST /admin-api/kjx/self/member`

```json
{
  "douyinId": "zhangsan",
  "subscribedMonths": 15,
  "subscribedDays": 435,
  "expireAt": "2027-08-09T23:59:59.999+08:00",
  "remark": ""
}
```

- 月数、天数、到期时间均必填。
- 服务端不得推导任一字段。
- 成功后生成 `{HOST_CODE}-{8位随机码}`。

### 5.4 作品与解锁

- `GET /app/works`
- `GET /app/works/{workCode}`
- `POST /app/works/{workCode}/unlock`
- `GET /app/entitlements`

解锁：

- `scene=unlock`、`ref_id=work_id`。
- 首先检查已有 entitlement；已有则返回 `1010`，不检查会员状态。
- 新解锁才检查 `MATCHED + VALID + quota + published`。

### 5.5 后台

- `/admin-api/kjx/self/member/**`
- `/admin-api/kjx/self/binding/**`
- `/admin-api/kjx/member/**`
- `/admin-api/kjx/binding/**`
- `/admin-api/kjx/appeal/**`
- `/admin-api/kjx/unlock-record/**`
- `/admin-api/kjx/work/**`

所有关键修改必须带 `reason` 并写 AUDIT。

---

## 6. 事务与事件矩阵

### 6.1 绑定审核通过

同一事务：

1. 锁定并校验 binding 状态。
2. 更新 binding 为 MATCHED。
3. 更新 member 绑定字段。
4. 更新 account.bind_status。
5. 写 AUDIT。
6. 写通知 Outbox。

任一步失败全部回滚。事务外不得直接调用 SMS4J。

### 6.2 新解锁

同一事务：

1. 插入/读取 idempotency guard。
2. 检查已有 entitlement。
3. 校验绑定、VALID、作品可见性。
4. 单 SQL CAS 更新 member 计数。
5. 插入 entitlement。
6. 插入 unlock_record。
7. 写 AUDIT/BIZ 与 Outbox。
8. 更新 guard 为 SUCCEEDED 和结果快照。

DuplicateKey 必须回滚 CAS 后再读取既有 entitlement 返回。

### 6.3 人工权益调整

同一事务：

1. 校验权限和 reason。
2. 增删 entitlement。
3. `MemberRecalcService` 重算。
4. 写 unlock_record `source=official_*`。
5. 写 AUDIT 与 Outbox。

### 6.4 通知投递

Outbox 消费者：

1. 以 `event_id + channel` 做消费幂等。
2. 站内信先落 MySQL。
3. 短信调用 SMS4J。
4. 失败退避重试；最终失败标 DEAD 并告警。

---

## 7. Tool 与买断权益

`NewUnlockPolicy` 与 `EntitlementAccessPolicy` 必须是两个独立策略：

- `NewUnlockPolicy`：绑定、VALID、额度、作品状态。
- `EntitlementAccessPolicy`：登录、entitlement、会话归属、剧情条件、频控。

禁止 Tool、Runtime 或存档接口调用 `NewUnlockPolicy`。

最小测试矩阵：

- VALID + entitlement：允许。
- EXPIRED + entitlement：允许。
- NOT_STARTED + entitlement：允许。
- VALID + 无 entitlement：拒绝。
- EXPIRED + 无 entitlement：拒绝。
- entitlement 属于其他账号/会员：拒绝并写 SEC。

---

## 8. 验收基线

### Gate A：认证隔离

- app token 调 admin 返回 401。
- admin token 调 app 返回 401。
- 无 token 调受保护接口返回 401。

### Gate B：部门隔离

- HOST A 看不到 HOST B 的 member、binding、entitlement、unlock-record。
- 直接传 B 的 ID 查询、修改、审核、导出均失败。

### Gate C：月/天独立

- 可保存 15 月 + 435 天。
- 修改月份不改变 available_count。
- 修改天数不改变绑定 claim 匹配值。
- 代码扫描无 `subscribedMonths * 30`。

### Gate D：幂等与并发

- 同 scene/ref/key 同请求只执行一次。
- 同 key、不同 ref 可独立执行。
- 同 scene/ref/key 不同请求返回 409。
- 额度为 10，50 VU/200 次并发新解锁成功数恰好 10。

### Gate E：Outbox

- 业务提交后进程崩溃，重启仍能投递。
- 重复消费不重复发通知或写投影。
- 业务回滚时不存在 Outbox 脏事件。
- DEAD 事件有告警和人工重放入口。

### Gate F：买断权益

- 会员过期不能新解锁。
- 会员过期可访问已有作品、Tool、会话与存档。

---

## 9. 变更控制

任何 Agent 修改以下内容前必须提交 ADR：

- API 路径或错误码。
- DDL 主键、唯一键、字段语义。
- 模块依赖方向。
- 事务边界。
- Outbox 事件结构。
- 月数/天数语义。
- 买断权益校验规则。

ADR 合并后，必须同步本文、OpenAPI、Flyway、测试与受影响客户端。

---

**文档结束**

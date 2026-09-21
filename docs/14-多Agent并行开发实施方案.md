# 叩君芯会员产品平台 · 多 Agent 并行开发实施方案
## Multi-Agent Delivery Plan v1.0

> **状态**：可直接分派  
> **前置基线**：`13-统一架构DDL与接口契约基线.md`  
> **目标**：让集成 Agent 可按波次派发独立任务，业务 Agent 不需要重新做架构决策。

---

## 0. 使用方法

1. 先由集成 Agent 完成 WP-00。
2. 每个工作包分配一个主 Agent；测试可分配独立验证 Agent。
3. Agent 开始前必须阅读本文、`13` 及本工作包引用的文档。
4. Agent 只能修改“文件所有权”范围；公共文件改动提交给集成 Agent。
5. 每个工作包必须独立编译、独立测试、提供变更说明和剩余风险。
6. Gate 未通过时，不向后合并依赖它的工作包。

### 0.1 标准派发提示词

```text
仓库：d:\koujunxin_web
工作包：WP-XX
必读：docs/13-统一架构DDL与接口契约基线.md
任务：严格完成 docs/14-多Agent并行开发实施方案.md 中 WP-XX 的全部内容。
限制：
1. 只修改 WP-XX 的文件所有权范围；
2. 不改变冻结的 API、DDL 语义、错误码、模块依赖和事务边界；
3. 需要改变基线时停止实现并提交 ADR 建议；
4. 完成后运行本工作包要求的测试；
5. 返回变更文件、测试结果、未完成项、风险、需要其他工作包配合的事项。
```

### 0.2 每个 Agent 的完成报告

- 完成的功能点。
- 修改/新增文件。
- DDL/API/事件变更。
- 已执行测试及结果。
- 未执行测试及原因。
- 已知风险。
- 对下游 Agent 的使用说明。

---

## 1. 仓库与文件所有权

```text
/
├── docs/
├── contracts/
│   ├── openapi/
│   ├── schemas/
│   ├── events/
│   └── errors.yaml
├── backend/
│   ├── pom.xml
│   ├── ruoyi-common/
│   ├── kjx-modules/
│   │   ├── kjx-account/
│   │   ├── kjx-member/
│   │   ├── kjx-work/
│   │   ├── kjx-runtime-api/
│   │   ├── kjx-runtime/
│   │   ├── kjx-tool/
│   │   ├── kjx-ai/
│   │   ├── kjx-channel/
│   │   ├── kjx-billing/
│   │   ├── kjx-notification/
│   │   └── kjx-log/
│   └── kjx-server/
├── apps/
│   ├── web/
│   └── admin/
├── tests/
└── deploy/
```

### 1.1 公共文件

以下只由 WP-00/集成 Agent 修改：

- 根 POM、模块聚合 POM。
- `contracts/errors.yaml`。
- OpenAPI 总入口。
- CI 工作流。
- Docker Compose 总入口。
- `kjx-server` 总装配。
- Flyway 版本段分配。

业务 Agent 如需修改，提交单独的小补丁或说明，不直接重构。

### 1.2 Flyway 所有权

- WP-02：V100–V149。
- WP-10：V200–V249。
- WP-11/WP-13：V300–V349。
- WP-12/WP-31：V400–V449。
- WP-30：V500–V549。
- WP-40/WP-41/WP-42：V600–V649。
- WP-50：V700–V749。

---

## 2. 波次与并行关系

### Wave 0：冻结基线

- WP-00 契约与工程骨架。

### Wave 1：基础设施

- WP-01 RuoYi、数据库、认证隔离。
- WP-02 runtime-api、幂等、Outbox。
- WP-20/WP-21 可基于 Mock 开始前端骨架。

### Wave 2：无 AI 业务

- WP-10 账号。
- WP-12 作品目录。
- WP-50 日志基础可并行。
- WP-11 依赖 WP-10 的账号上下文，可在接口冻结后并行开发大部分功能。

### Wave 3：权益闭环

- WP-13 解锁与权益。
- WP-20/WP-21 接真实 API。

### Wave 4：平台能力

- WP-30 通道、计费、支付。
- WP-31 作品包、内容、素材、音乐。

### Wave 5：核心运行时

- WP-40 AI 与会话。
- WP-41 Tool。
- WP-42 Runtime。

三者先冻结端口和测试桩，再并行。

### Wave 6：集成上线

- WP-50 完成分析与观测。
- WP-51 E2E、安全、压测、部署与恢复。

---

## 3. 工作包

## WP-00 契约冻结与工程骨架

### 目标

创建可编译的单仓库骨架，让所有业务 Agent 有稳定目录、契约与版本边界。

### 依赖

无。

### 文件所有权

- 根目录构建文件。
- `contracts/**`。
- `backend/pom.xml`、各空模块 POM。
- `apps/web`、`apps/admin` 初始骨架。
- CI、格式化、静态检查配置。

### 开发步骤

1. 创建单仓库目录。
2. 导入 RuoYi-Vue-Plus v5.2.3，保留必要 common 与后台模块。
3. 创建唯一启动模块 `kjx-server`。
4. 创建 11 个自研模块空壳。
5. 将 `13` 的 API 路径与错误码写入 `contracts/`。
6. 创建 OpenAPI 分片约定和事件 Schema 目录。
7. 配置 Java/TS 格式化、单测、构建。
8. 配置 ArchUnit/Maven Enforcer 依赖规则。
9. 编写 README：本地启动、迁移、测试、模块边界。

### 验收

- `mvn test` 在空业务模块下通过。
- Next.js 与后台前端能启动空页。
- CI 执行后端编译、前端检查、契约校验。
- 人为添加 runtime→tool 与 tool→runtime 依赖时 CI 失败。
- 新 Agent 按 README 在 30 分钟内启动工程。

### 注意事项

- 不实现业务。
- 不把所有 RuoYi 模块无差别复制进来。
- 公共 DTO 只在契约或公开 api 包定义。

### 交付

- 可编译骨架。
- OpenAPI/错误码基线。
- 模块依赖门禁。
- 文件所有权清单。

---

## WP-01 基础设施、RuoYi 与认证隔离

### 目标

跑通 MySQL、Redis、Flyway、RuoYi 后台和前后台两套隔离认证。

### 依赖

WP-00。

### 文件所有权

- `backend/ruoyi-common/**`
- `backend/kjx-server/**` 的基础配置
- `deploy/compose/**`
- `deploy/nginx/**`
- V1/RuoYi baseline

### 开发功能

1. MySQL 8.4、Redis 8 Docker Compose。
2. MySQL utf8mb4、Asia/Shanghai、慢查询。
3. RuoYi SQL 整理为 Flyway baseline。
4. Sa-Token `app` / `admin` 双域。
5. 部门树与 HOST/OPERATOR/PUBLISHER/FINANCE 角色。
6. `@DataPermission` 基础配置。
7. Actuator health、readiness、graceful shutdown。
8. Redisson 锁、限流、延迟队列兼容性验证。

### 验收

- 空库一键迁移成功，重复启动无手工步骤。
- Emoji 可写入读出。
- app token 调 admin 为 401，admin token 调 app 为 401。
- HOST A 无法访问 HOST B 的测试表数据。
- Redisson 三项能力通过；失败则全局回退到统一 3.x 版本。

### 注意事项

- 不允许 `baseline-on-migrate` 掩盖生产库状态错误。
- 数据权限必须覆盖按 ID 查询和更新，不只覆盖分页。

### 交付

- Gate A/Gate B 基础测试。
- 初始化数据迁移。
- 环境启动文档。

---

## WP-02 runtime-api、幂等与 Outbox

### 目标

建立跨模块中立端口、统一幂等组件和可靠副作用基础设施。

### 依赖

WP-00。

### 文件所有权

- `kjx-runtime-api/**`
- `kjx-notification/**` 的基础设施
- 公共幂等包
- V100–V149
- `contracts/events/**`

### 开发功能

1. 定义 `SessionStateQueryPort`、`SessionStateCommandPort`。
2. 定义 `SessionAuthorizationPort`、`EntitlementAccessPort`。
3. 定义 `ToolExecutionPort` 与中立 DTO。
4. 实现 `IdempotencyService`：
   - 首次占位；
   - 同键同参返回；
   - 同键异参冲突；
   - 同事务完成结果。
5. 实现 `OutboxWriter`：JSON 列写入完整 envelope（含嵌套 payload 与 occurredAt/traceId/requestId/idempotencyKey），Publisher 明确收到信封。
6. 实现轮询领取、超时回收、重试、DEAD。
7. 定义消费者幂等接口，并用 V102 回执表实现：`tryBegin` 原子领取新行或回收过期 PROCESSING；DONE 永不重复；abandon 可释放 PROCESSING。
8. 提供测试消费者与故障注入。
9. 外部 SMS 仍按至少一次投递，向支持幂等的提供方传 `eventId`。

### DDL/API

严格使用 `13` 中 `kjx_idempotency_guard`、`kjx_outbox_event`（**冻结字段，禁止增删列**）。

消费回执支持表 `kjx_outbox_consume_receipt`（V102，非冻结）按 `docs/adr/ADR-001-outbox-consume-receipt.md` 与 `13` §3.5：主键 `(event_id, channel)`，含 `lease_until` / `update_time` / `attempt_count`。

信封对齐 `contracts/events/outbox-envelope.schema.json`。

### 验收

- 同 key/scene、不同 ref 独立执行。
- 同 key/scene/ref 不同请求返回 409。
- 事务回滚无 guard 成功占位、无 Outbox。
- 提交后宕机，重启仍投递。
- 写入 JSON 为完整 envelope；重复投递对 DONE 的本地消费只生效一次。
- PROCESSING 回执崩溃且租约到期后可被 tryBegin 回收；DONE 不可回收。
- runtime/tool 编译依赖无环。

### 注意事项

- 不承诺“恰好一次”。外部 SMS 是至少一次，即使本地回执已 DONE，提供方仍可能见过同一 `eventId`。
- ApplicationEvent 不能替代 Outbox。
- Outbox JSON 必须是脱敏后的完整信封，嵌套 payload 带 schemaVersion；事件特定校验针对嵌套 payload。

### 交付

- 可被所有模块调用的幂等/Outbox API。
- 单元与 Testcontainers 测试。
- 事件编写规范。

---

## WP-10 账号、认证、验证码与设备

### 目标

完成手机号注册、登录、安全与设备管理。

### 依赖

WP-01、WP-02。

### 文件所有权

- `kjx-account/**`
- V200–V249
- `contracts/openapi/account*`

### 开发功能

1. 图形验证码校验。
2. 短信验证码发送与 Redis 哈希校验。
3. 手机号注册。
4. 密码登录、验证码登录。
5. AES-256-GCM 手机号加密。
6. HMAC-SHA256 唯一索引。
7. 密码哈希、失败锁定。
8. 设备 upsert、列表、下线。
9. 修改/找回密码。
10. 七天注销冷静期与撤销。
11. 安全通知写 Outbox。

### 验收

- 验证码单次使用、过期、频控通过。
- 同手机号并发注册只有一个成功。
- 连续五次失败锁 15 分钟。
- DB 无明文手机号、密码、token。
- 日志无手机号明文、验证码与 Authorization。
- 注册/两种登录/设备/注销有集成测试。

### 注意事项

- 密钥来自环境/KMS，不进 Git。
- `token_hash` 替代明文 token。
- 不开放邮箱注册。

### 交付

- 账号 OpenAPI。
- 密钥轮换说明。
- 前端 Mock 示例。

---

## WP-11 主播、会员与绑定审核

### 目标

跑通主播录会员、随机编码、三要素绑定、人工审核和申诉。

### 依赖

WP-01、WP-02、WP-10。

### 文件所有权

- `kjx-member/**` 中 member/binding/appeal
- V300–V329
- 对应 OpenAPI

### 开发功能

1. `kjx_host` 部门扩展。
2. HOST 录入会员。
3. 月数、天数、到期时间分别必填。
4. 随机会员编码，去除 O/0/I/1/L。
5. 唯一 `MemberRecalcService`。
6. 三要素规范化与精确匹配。
7. 失败三次锁 30 分钟。
8. PENDING_REVIEW 人工审核。
9. 审核通过业务三表 + AUDIT + Outbox 同事务。
10. 驳回、解绑、强制解绑和申诉。
11. `dept_id` 数据权限。

### 验收

- 15 月 + 435 天可保存。
- 改月数不改额度；改天数不改绑定匹配。
- 代码无月/天换算。
- 并发审核同一会员只有一个成功。
- 事务任一步故障全部回滚。
- HOST A 无法操作 B 的 member/binding。
- 通知重复消费不重复发。

### 注意事项

- 用户 claimMonths 不参与额度计算。
- 短信不得在事务内直接发送。
- `dept_id` 从 member 快照写入 binding。

### 交付

- 主播录入到审核完成的集成测试。
- 后台接口权限矩阵。

---

## WP-12 作品目录与版本基础

### 目标

提供作品 CRUD、列表、详情、状态机与稳定灰度规则。

### 依赖

WP-01、WP-02。

### 文件所有权

- `kjx-work/**` 基础目录
- V400–V419
- 作品 OpenAPI

### 开发功能

1. 作品 CRUD。
2. 作品/内容版本分离。
3. 状态机与发布字段。
4. `WorkVisibilityService`。
5. 列表、详情。
6. 下架但已解锁可访问规则的查询端口。

### 验收

- 列表、详情、解锁使用同一个可见性服务。
- GRAY 对同一账号稳定。
- 下架作品不在公开列表。
- 已有 entitlement 时详情仍可访问。

### 注意事项

- 本包不实现解锁。
- 不读取 member Mapper。

### 交付

- 稳定作品只读契约。
- 供 WP-13/WP-20 使用的测试桩。

---

## WP-13 权益、人工调整与原子解锁

### 目标

建立 entitlement 事实源和并发安全的新解锁链路。

### 依赖

WP-02、WP-11、WP-12。

### 文件所有权

- `kjx-member/**` 中 entitlement/unlock
- V330–V349
- 解锁 OpenAPI

### 开发功能

1. `EntitlementAccessPort` 实现。
2. 解锁前先查已有 entitlement。
3. `NewUnlockPolicy`。
4. 单 SQL CAS。
5. entitlement、unlock_record、AUDIT、Outbox 同事务。
6. 幂等 guard 集成。
7. 人工增删权益与重算。
8. 失败记录 Redis 窗口合并。
9. 三表 `dept_id`。

### 验收

- available=10，50 VU/200 次并发成功恰好 10。
- entitlement 数 = unlocked_count。
- 每个 member/work 只有一个成功事实。
- EXPIRED + entitlement 可访问。
- EXPIRED + 无 entitlement 不可新解锁。
- 同键异参 409。
- 故障注入无 CAS 计数漂移。

### 注意事项

- entitlement 是访问事实源，不能从计数字段推断。
- DuplicateKey 必须回滚 CAS。
- Tool 不可复用 `NewUnlockPolicy`。

### 交付

- k6 脚本与 SQL 断言。
- 并发/幂等/故障注入报告。

---

## WP-20 Next.js 会员前台

### 目标

实现 M1/M2 用户旅程，并为 M5 Runtime 预留边界。

### 依赖

WP-00；可先 Mock，并在 WP-10～13 后接真实 API。

### 文件所有权

- `apps/web/**`，不含后续 Runtime 专属目录。

### 开发功能

1. 注册、密码/验证码登录。
2. httpOnly Cookie 与 CSRF。
3. 个人主页。
4. 三要素绑定和五种状态。
5. 作品列表、详情、解锁确认。
6. 我的解锁。
7. 统一错误码与请求 ID 展示。
8. 响应式与基础无障碍。

### 验收

- Playwright 跑通注册→绑定→审核后刷新→解锁。
- 浏览器 JS 无法读取 token。
- 不在前端计算会员状态/额度。
- 过期用户可从我的解锁进入已购作品。
- API Mock 与真实响应契约一致。

### 注意事项

- 不保存敏感 token 到 localStorage。
- 业务判断以后端为准。

### 交付

- E2E 测试。
- 页面与接口映射清单。

---

## WP-21 RuoYi 管理后台

### 目标

实现主播、运营和管理员的 M1/M2 后台页面。

### 依赖

WP-00、WP-01；真实集成依赖 WP-11～13。

### 文件所有权

- `apps/admin/**`

### 开发功能

1. 部门页扩展主播信息。
2. HOST 会员录入。
3. 月数/天数独立必填。
4. 会员编辑、续期、理由确认。
5. 绑定审核与申诉。
6. 作品管理。
7. 解锁记录与导出。
8. 角色菜单与按钮权限。

### 验收

- HOST/OPERATOR/ADMIN 三角色回归。
- HOST A 通过 UI 和直接请求均无法访问 B。
- 修改关键字段无 reason 前后端均拒绝。
- 月数/天数无自动联动。

### 注意事项

- 前端隐藏不是权限控制。
- 不自行定义与 OpenAPI 不同的字段。

### 交付

- 页面权限矩阵。
- 后台 E2E 或关键组件测试。

---

## WP-30 双通道、计费、订单与支付

### 目标

完成 PLATFORM/BYOK、冻结结算、账本、订单和支付回调。

### 依赖

WP-02、WP-10。

### 文件所有权

- `kjx-channel/**`
- `kjx-billing/**`
- V500–V549
- 通道/计费/支付契约

### 开发功能

1. 通道优先级解析。
2. BYOK 本地/托管凭据。
3. SSRF DNS 解析后复验、重定向复验。
4. PLATFORM 预冻结、结算、释放。
5. append-only ledger。
6. usage_event。
7. 支付宝/微信订单、回调验签和主动查单。
8. 对账与差异告警。
9. 账本/订单 Outbox。

### 验收

- BYOK 失败绝不自动扣平台余额。
- 重复回调只入账一次。
- 账本、余额和 usage 可对账为零差异。
- 结算故障全部回滚或可补偿。
- ClickHouse 停止时余额与支付正常。

### 注意事项

- 余额只从 MySQL 账本派生。
- Key 不入日志。
- 支付真实环境需独立安全评审。

### 交付

- 沙箱支付报告。
- 账本不变量测试。

---

## WP-31 作品包、内容、素材与音乐

### 目标

完成安全上传、校验、版本发布、素材和音乐分发。

### 依赖

WP-12。

### 文件所有权

- `kjx-work/**` package/content/asset/music
- V420–V449

### 开发功能

1. 流式上传与大小限制。
2. 隔离目录解压。
3. Zip Slip、压缩炸弹、文件数检查。
4. manifest/checksum/Schema 校验。
5. SVG script/on*/外链检测。
6. `server/` 与 CDN 产物隔离。
7. OSS 元数据和签名 URL。
8. 发布、灰度、回滚。
9. Form A 示例包。

### 验收

- 恶意 zip、超限、外链、脚本 SVG 全部拒绝。
- `server/` 任一文件无法通过 CDN 获取。
- 回滚恢复旧版本。
- 一个 Form A 包可上传、校验、预览、发布。

### 注意事项

- 禁止在 Web 进程内信任 zip 路径。
- 所有外部 URL 默认拒绝。

### 交付

- 示例包。
- 15 项机审报告。

---

## WP-40 AI、Prompt 与会话

### 目标

完成 OpenAI 兼容 SSE、Prompt 组装、会话记忆和模型降级。

### 依赖

WP-02、WP-30、WP-31。

### 文件所有权

- `kjx-ai/**`
- V600–V619
- `/v1/**` 契约

### 开发功能

1. 稳定前缀/可变后缀。
2. Prompt 版本。
3. 会话、消息、摘要。
4. 模型路由。
5. OpenAI request/response/stream。
6. Tool Calling/JSON Schema/纯文本三级降级。
7. SSE 取消、心跳、超时。
8. Langfuse 旁路上报。

### 验收

- 平台与 BYOK 各跑通真实一轮。
- Prompt 母版不下发。
- Langfuse 故障不阻断。
- SSE 断线/取消不继续无限消耗。
- 结构化失败可降级为可读文本。

### 注意事项

- 不直接写 runtime 状态。
- Tool 调用只通过端口。

### 交付

- OpenAI 兼容测试。
- SSE 时序与超时报告。

---

## WP-41 Tool 与声明式编排

### 目标

实现 builtin/compose 工具、前置校验、LiteFlow 与声明式 UI。

### 依赖

WP-02、WP-13；与 WP-40/WP-42 按端口并行。

### 文件所有权

- `kjx-tool/**`
- V620–V629
- Tool Schema

### 开发功能

1. 工具注册与版本。
2. 参数 JSON Schema。
3. builtin 工具。
4. LiteFlow compose。
5. entitlement、session、剧情、频控校验。
6. runtime-api 状态读写。
7. UI 节点白名单与 props Schema。
8. 重试、超时、重复调用短路。

### 验收

- EXPIRED + entitlement 可调用。
- 无 entitlement 拒绝。
- 其他账号 session 拒绝。
- 代码无 member VALID 判断。
- 不支持 script。
- runtime/tool 无直接依赖。
- 非白名单 UI 节点拒绝。

### 注意事项

- 买断不等于跳过登录、会话和剧情条件。
- 组合事务边界要明确，禁止给每个步骤随意加独立事务。

### 交付

- 内置与组合工具样例。
- 权益测试矩阵。

---

## WP-42 Runtime 后端与渲染器

### 目标

实现会话运行时、存档、分段披露、Form A 渲染器和沙箱宿主。

### 依赖

WP-02、WP-13、WP-31；与 WP-40/WP-41 按端口并行。

### 文件所有权

- `kjx-runtime/**`
- `apps/web/runtime/**`
- V630–V649

### 开发功能

1. 会话与存档。
2. SessionState 端口实现。
3. 阶段内容、布局、素材签名。
4. Form A 固定组件渲染器。
5. Form B/C iframe sandbox。
6. postMessage nonce、origin、方法白名单。
7. 宿主代理 API，不向 iframe 传 token。
8. 调用 `ToolExecutionPort`。

### 验收

- EXPIRED + entitlement 可继续会话/存档。
- 无 entitlement 不下发真实内容。
- iframe 获取不到 token/Cookie/主站 DOM。
- 跨作品、跨账号、跨 session 访问拒绝。
- 一个真实作品完成进入→对话→Tool→存档。

### 注意事项

- 不依赖 `kjx-tool` 实现 jar。
- 存档必须校验 schema/version/checksum。

### 交付

- Runtime E2E。
- 沙箱安全用例。

---

## WP-50 日志、ClickHouse 与可观测

### 目标

完成结构化日志、脱敏、分析投影和可观测链路。

### 依赖

WP-02；可贯穿各波次。

### 文件所有权

- `kjx-log/**`
- V700–V749
- `deploy/observability/**`

### 开发功能

1. request_id/trace_id/MDC。
2. 统一脱敏。
3. MySQL AUDIT/BILLING。
4. Outbox → ClickHouse 投影。
5. 投影幂等、重放与对账。
6. OTel、指标、Loki/Tempo。
7. 成本、错误、Outbox 积压告警。

### 验收

- ClickHouse 停机不影响核心业务。
- 恢复后可重放且无重复。
- 核心领域无 ClickHouse 查询依赖。
- 手机号、Key、Authorization、secretRef 不出日志。
- trace_id 可串起入口、业务、模型、Tool。

### 注意事项

- AUDIT/BILLING 与业务同事务。
- 分析日志允许最终一致。

### 交付

- 日志 Schema。
- 看板与告警清单。
- 故障恢复报告。

---

## WP-51 集成、安全、压测、备份与部署

### 目标

把所有工作包组装成生产候选并验证回滚和恢复能力。

### 依赖

全部功能工作包。

### 文件所有权

- `tests/**`
- `deploy/**`
- CI 集成与发布流程
- 不重构业务实现

### 开发功能

1. Flyway 空库和升级库测试。
2. OpenAPI 契约回归。
3. Gate A/B 横向越权。
4. 绑定/解锁/账本故障注入。
5. k6 并发。
6. SSE 100 长连接。
7. gitleaks、SCA、安全头、依赖漏洞。
8. MySQL 全量 + binlog、Redis AOF、对象存储版本。
9. 新实例恢复演练。
10. staging 部署、回滚、运行手册。

### 验收

- 所有 Gate 全绿。
- 100 SSE 长连接下线程池、连接池无耗尽。
- 解锁成功数和额度严格一致。
- Outbox 积压可告警、恢复、人工重放。
- MySQL 可恢复到新实例。
- 无高危未处置漏洞。
- staging 连续稳定运行达到约定观察期。

### 注意事项

- 压测环境不得使用生产短信/支付。
- 备份成功不等于恢复成功，必须实际恢复。

### 交付

- 发布/回滚/恢复手册。
- 验收报告。
- 已知风险与上线决策记录。

---

## 4. Gate 与合并顺序

### Gate 0 契约冻结

- 13 已评审。
- OpenAPI/错误码/事件 Schema 已建立。
- Flyway 版本段与文件所有权已锁定。

### Gate 1 工程可运行

- 后端、前台、后台、MySQL、Redis可启动。
- 空库迁移通过。

### Gate 2 安全隔离

- app/admin token 隔离。
- member/binding/entitlement/unlock-record 部门隔离。

### Gate 3 M1 会员闭环

- 主播录入、注册、绑定、审核、Outbox 通知完成。
- 月/天独立验收通过。

### Gate 4 M2 解锁闭环

- 并发、幂等、权益买断验收通过。

### Gate 5 平台能力

- 双通道、账本、支付沙箱、作品包完成。

### Gate 6 AI/Tool/Runtime

- 无依赖环。
- 买断 Tool 鉴权正确。
- 真实作品全链路完成。

### Gate 7 生产候选

- 安全、容量、故障、备份、恢复、回滚全绿。

### 合并顺序

1. WP-00。
2. WP-01、WP-02。
3. WP-10。
4. WP-11、WP-12。
5. WP-13。
6. WP-20、WP-21 真实集成。
7. WP-30、WP-31。
8. WP-40、WP-41、WP-42。
9. WP-50。
10. WP-51。

---

## 5. 集成 Agent 每轮检查

- 是否修改了其他 Agent 文件。
- 是否新增未登记 API/错误码。
- 是否更改字段语义或事务边界。
- 是否创建跨模块 Mapper/Entity 依赖。
- 是否使用 `/api/**` 旧路径。
- 是否出现月数/天数换算。
- 是否直接发短信而未写 Outbox。
- 是否从 ClickHouse 查询业务状态。
- 是否在 Tool/Runtime 校验会员 VALID。
- 是否新增明文密钥、手机号或 token 日志。
- 是否提交测试和迁移回滚说明。

---

## 6. 完成度定义

工作包只有同时满足以下条件才可标记完成：

1. 功能代码完成。
2. DDL/API/事件契约同步。
3. 单元、集成和本包验收测试通过。
4. 无新增 lint/编译错误。
5. 安全与数据权限用例通过。
6. README/运行说明更新。
7. 完成报告包含风险和下游说明。
8. 集成 Agent 复核文件边界。

“代码已写但未验证”“接口可用但契约未更新”“主流程能跑但越权测试未做”均不算完成。

---

**文档结束**

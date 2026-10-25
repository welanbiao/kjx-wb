# WP-41 集成说明

状态：模块内实现与测试已落地。未改 `kjx-runtime-api` 端口、`kjx-runtime`、`kjx-ai`、`kjx-work`、`kjx-member`、`kjx-channel`、`kjx-billing`、`kjx-server`、根 POM、`contracts/`、`docs/`、`apps/`、`deploy/`。

## 行为

- `ToolExecutionPort` 由 `com.koujunxin.tool.execute.ToolExecutor` 实现。只依赖 `kjx-runtime-api`。
- 前置校验顺序：参数 JSON Schema → `EntitlementAccessPort.requireEntitlement` → `SessionAuthorizationPort.requireOwner` → 会话阶段/payload 前置条件 → 频控 → 幂等认领。
- **不判断会员 VALID/EXPIRED**。EXPIRED 但 entitlement 端口放行时必须能调用；无权益抛 `EntitlementRequiredException`；其他账号 session 抛 `SessionNotOwnedException`。
- 实现种类仅 `builtin` / `compose`。`script` 在注册与执行时拒绝，错误码 `TOOL_SCRIPT_FORBIDDEN`。
- 组合工具使用明确 `steps[]`。不为每一步开启事务：步骤在内存 `WorkingState` 上顺序执行，状态通过 `SessionStateCommandPort.apply` **一次**写出；本模块 JDBC 事务只覆盖幂等认领+频控，以及结果落库。
- 同一 `session_id + idempotency_key`：同参短路返回原结果；异参返回 `IDEMPOTENCY_CONFLICT`。
- 超时返回 `TOOL_TIMEOUT`，文案「信息不可用」。`retry_max` 仅对 `ToolFailure.retryable(...)` 生效。
- `node.type` 必须在 `kjx_ui_node_type` 白名单（或 `custom.slot:{name}`）。props 走 Schema。禁止 `html` / `script` / `style` 字段。

## Flyway

位置：`kjx-tool/src/main/resources/db/migration`，对应 `classpath:db/migration`。禁止 `classpath*:`。

| 版本 | 脚本 |
|---|---|
| V620 | `kjx_tool` |
| V621 | `kjx_tool_version` |
| V622 | `kjx_tool_work_binding` |
| V623 | `kjx_tool_call` |
| V624 | `kjx_tool_rate_bucket` |
| V625 | `kjx_ui_node_type` |
| V626 | UI 白名单种子 |
| V627 | builtin 种子 |
| V628 | 组合样例 `metric.adjust` |
| V629 | 共用 L2：`persona.assemble`、`chat.group.send`；`chat.bubble` 增加可选 `speaker` |

WP-40/WP-42 请避开 V620–V629。

共用 L2 工具（不绑定某一作品）：`persona.assemble` 只把人设拼进 `payload.prompt.persona`（不进 UI）；`chat.send` 追加 `payload.messages` 与 `chat.bubble`；`chat.group.send` 追加 `payload.groupMessages` 与带 `speaker` 的 `chat.bubble`；`redpacket.send` 余额不足时 `PRECONDITION_FAILED` 且不改钱包。

## 待主 Agent 接线

1. `kjx-server` 依赖 `kjx-tool`，并提供 `EntitlementAccessPort`（WP-13 已有）、`SessionAuthorizationPort` / `SessionStateQueryPort` / `SessionStateCommandPort`（WP-42）。无这些 Bean 时本包不创建 `ToolExecutionPort`。
2. 确认 Flyway 为 `classpath:db/migration`，能扫到 V620–V629。
3. `ToolAutoConfiguration` 已写入 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
4. `SessionStateCommandPort.apply` 已识别 `type=TOOL_PATCH`，把 `payloadJson` 整份替换为会话状态快照。
5. 登录态由调用方保证 `accountId > 0`；本包不再查账号表。

## 契约缺口（未改 `contracts/`）

OpenAPI / `errors.yaml` 尚无 Tool 路径与错误码。端口失败除冻结的 `ENTITLEMENT_REQUIRED` / `SESSION_NOT_OWNED` 外，本包 `ToolResult.code` 使用：

| code | 含义 |
|---|---|
| `TOOL_NOT_FOUND` | 无此工具版本 |
| `TOOL_DISABLED` | 未启用或 custom 未绑定作品 |
| `TOOL_SCRIPT_FORBIDDEN` | script / 非法 compose op |
| `INVALID_ARGUMENTS` | 参数未过 JSON Schema |
| `PRECONDITION_FAILED` | 阶段/断言失败 |
| `RATE_LIMITED` | 频控或冷却 |
| `IDEMPOTENCY_CONFLICT` | 同键异参（建议复用 1090） |
| `TOOL_IN_PROGRESS` | 同键仍在执行 |
| `TOOL_TIMEOUT` | 超时 |
| `UI_NODE_NOT_ALLOWED` | 非白名单 node.type |
| `UI_PROPS_INVALID` | props Schema 失败或禁止字段 |
| `SESSION_WORK_MISMATCH` | command.workCode 与 session 不一致 |
| `TOOL_NOT_IMPLEMENTED` | builtin handler 缺失 |
| `COMPOSE_FAILED` | 编排错误 |

建议后续在 `contracts/openapi/paths/` 增加 admin 工具注册分片（若需要 HTTP），并在 `errors.yaml` 分配业务码。事件 `analytics.tool_call.projected` 已在白名单，本包未写 Outbox（避免反向依赖 `kjx-log` / 改事件契约）。

## 未做

- 未引入 LiteFlow；用显式 `steps[]` 替代，避免脚本引擎。
- 未实现 `llm.generate` / `content.reveal` / 真实选曲签名（WP-40 / WP-31 / WP-42）。
- 未实现 compose `parallel`（会模糊事务边界）。`transaction` op 是外层工作单元的别名，不新开事务。
- 未做 HTTP 控制器、测试台、调用日志投影到 ClickHouse。
- 未做内容安全审核（WP-50）。
- 单回合工具次数上限需由 WP-40/WP-42 在调用本端口前计数。

## 独立验证

```text
mvn -f backend/pom.xml -pl kjx-modules/kjx-tool -am test
```

Testcontainers MySQL 8.4；`disabledWithoutDocker = true`。无 Docker 时跳过 IT。

# WP-40 集成说明

状态：模块内实现与测试已落地。未改 `openapi.yaml`、`errors.yaml`、事件白名单、`kjx-server`、根 POM、`kjx-channel`、`kjx-billing`、`kjx-work`、`kjx-runtime`、`kjx-tool`、`kjx-member`。

## 行为

- Prompt 分稳定前缀（第一条 system）与可变后缀（第二条 system：状态/摘要）。WORK 版本可激活；METHOD 母版只进 `MethodPromptStore`，不进入运行时消息，不经 `PromptCatalog` 读出。
- 会话/消息/滚动摘要在 `kjx_ai_session` / `kjx_ai_message` / `kjx_ai_summary`。不写 runtime 剧情状态。
- 模型路由在本模块用 `AiChannelMode.PLATFORM|BYOK` 表达。缺少 BYOK 凭据直接失败，不回退平台、不冻结余额。
- OpenAI 兼容 request/response/stream（`OpenAiCodec` + `ChatCompletionService`）。客户端 `system` 在 SERVER 模式下丢弃。HTTP 适配器应只传入本轮新的 user 消息，不要把整段历史再 append 一遍。
- 结构化输出：Tool Calling → JSON Schema → Prompt 约束解析；失败则降为可读文本。
- Tool 只走 `ToolExecutionPort`（`kjx-runtime-api`）。本模块不依赖 kjx-tool / kjx-runtime 实现。
- SSE：心跳、空闲超时、总超时；取消后停止拉取假上游，不再继续消耗。
- Langfuse 经 `SafeLangfuseReporter` 旁路，异常不阻断主请求。日志只打 session/turn/channel/model，不打 Prompt 母版、Key、会话正文。

## 待主 Agent 接线

1. `contracts/openapi` 目前声明 `/v1/**` 不收录。需要新增分片（建议 `paths/openai.yaml` + `schemas/openai.yaml`）并挂到 `openapi.yaml`：
   - `POST /v1/chat/completions`（`stream` 时 `text/event-stream`）
   - `GET /v1/models`
   - Header：`Authorization`、`X-KJX-Session-Id`、`X-KJX-Work-Id`、`X-KJX-Channel`、`X-KJX-Credential-Id`、`X-KJX-BYOK-Key`（LOCAL）、`X-Idempotency-Key`
2. `contracts/errors.yaml` 建议补：`1202 BYOK_FAILED`（若 WP-30 未合入）、`CREDENTIAL_INVALID`、`AI_UPSTREAM_TIMEOUT`（504）、`AI_CANCELLED`（499）、`PROMPT_NOT_FOUND`（404）。
3. `kjx-server` 提供 `UpstreamChatClient`（平台 Key 与 BYOK 转发）。**本模块故意不内置 HTTP 客户端**，避免误打真实模型。
4. 提供 `ToolExecutionPort`、`SessionStateQueryPort`、`SessionAuthorizationPort`、`EntitlementAccessPort`。禁止把 `SessionStateCommandPort` 注入本模块。
5. 把 `AiChargePort` 接到 WP-30 `PlatformBilling`：仅 PLATFORM 冻结/结算；BYOK 不得 freeze。
6. 提供 `LangfusePort` 实现；缺省是 no-op。故障必须继续吞掉。
7. HTTP 适配器（鉴权 + SSE `SseEmitter` 取消挂钩 `CancellationToken`）放在 server 或本模块后续增量，当前服务入口是 `ChatCompletionService`。
8. Flyway：`classpath:db/migration` 下 V600–V603。禁止 `classpath*:`。集成测试应断言这些版本出现在 `flyway_schema_history`。

## 未做

- 真实上游 SDK / Spring AI ChatClient
- Higress 虚拟 Key 与网关限流
- 断线后续传完整 token 重放（只保留 `last_turn_id`）
- Prompt A/B 灰度与后台生成作品 Prompt
- `/app/sessions` CRUD（偏 WP-42）；本包只覆盖对话记忆
- OpenAPI 文件（按所有权只写在本文）

## 版本

Flyway：`V600` Prompt 版本，`V601` 会话，`V602` 消息，`V603` 摘要。位置是 `classpath:db/migration`。

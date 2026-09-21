# 叩君芯契约基线（WP-00A）

本目录是后端 `kjx-server`、Next.js 前台、Vue 后台的**唯一接口/错误码/事件入口**。实现 Agent 不得在代码里另写一套路径、错误码或 Outbox 结构。

权威文档：`docs/13-统一架构DDL与接口契约基线.md`。冲突时以 13 为准，其次 `docs/11`。

## 目录

```text
contracts/
├── README.md                          # 本文件
├── errors.yaml                        # 冻结业务错误码（唯一来源）
├── schemas/
│   └── common.yaml                    # 统一响应、分页、错误、时间、请求 ID、公共 Header
├── openapi/
│   ├── openapi.yaml                   # OpenAPI 3.0.3 总入口
│   ├── paths/                         # 按域拆分的 Path Item
│   └── schemas/                       # account / member / work 请求响应
└── events/
    ├── outbox-envelope.schema.json    # Outbox 信封
    ├── notification.schema.json       # 通知投影 payload
    ├── entitlement.schema.json        # 权益投影 payload
    ├── unlock.schema.json             # 解锁记录投影 payload
    └── analytics.schema.json          # ACCESS / AI_CALL / TOOL_CALL 投影
```

## 冻结约定（实现时不得走样）

1. 路径只使用 `/app/**` 与 `/admin-api/kjx/**`。禁止 `/api/**`，不提供双轨。`/v1/**` 留给 M3。
2. `X-Idempotency-Key` **只走 Header**，禁止写入 JSON body。绑定 `scene=binding, ref_id=account_id`；解锁 `scene=unlock, ref_id=work_id`。
3. 主播录入会员必须同时提交 `subscribedMonths`、`subscribedDays`、`expireAt`。两字段互不换算，禁止 `months * 30` 或 `days / 30` 推导请求值。
4. 解锁 path 参数使用 `{workCode}`：`POST /app/works/{workCode}/unlock`。
5. 响应信封：`{ code, data, msg, requestId }`。HTTP 状态表达协议结果，`code` 表达业务结果。时间 ISO-8601 且带 `+08:00`。分页 `pageNo` / `pageSize`，响应 `list` / `total`。
6. 错误码常量只允许出现在 `errors.yaml`。新增或修改必须先 ADR。
7. 已有 entitlement 时解锁返回 HTTP 200、`code=1010`（`ALREADY_UNLOCKED`），不再检查会员 `VALID`。
8. Outbox 至少一次投递；payload 必须脱敏并带 `schemaVersion`。消费者按 `eventId`（通知再加 `channel`）幂等。

## 下游怎么用

| 角色 | 建议 |
|---|---|
| 后端（WP-10/11/12/13） | 按 `openapi.yaml` 实现 Controller；错误码从 `errors.yaml` 生成 Java 枚举，禁止手抄。 |
| Next.js（WP-20） | `openapi-typescript` 或 orval 生成类型；Mock 必须与 schema 一致。Token 放 httpOnly Cookie。 |
| Vue 后台（WP-21） | 同样从总入口生成 client。HOST 接口走 `/admin-api/kjx/self/**`。关键修改表单必须收集 `reason`。 |
| WP-02 | Outbox 写入必须符合 `events/outbox-envelope.schema.json`；通知/权益/分析 payload 分别校验对应 schema。 |

生成示例：

```bash
# TypeScript 类型（前台/后台）
npx --yes openapi-typescript contracts/openapi/openapi.yaml -o generated/openapi.ts

# 打包成单文件再给 Spring / 文档站
npx --yes @redocly/cli bundle contracts/openapi/openapi.yaml -o generated/openapi.bundle.yaml
```

相对 `$ref` 未打包前，部分生成器需要先 `bundle`。

## 变更流程

1. 需要改 API 路径、字段语义、错误码、Outbox 事件结构、月数/天数语义或买断校验时：**停止实现，提交 ADR**。
2. ADR 合并后，由集成 Agent / WP-00 同步：本文档目录、`docs/13`、OpenAPI、Flyway、测试与客户端生成物。
3. 业务 Agent 只允许在自己工作包的 OpenAPI 分片上**追加尚未冻结的字段说明**，不得改已冻结 path 与错误码。需要改公共文件时提交小补丁给集成 Agent。
4. 本目录文件所有权：WP-00 / 集成 Agent。`contracts/events/**` 的投递实现属 WP-02，但 schema 变更仍走本流程。

## 校验方式

仓库尚未接入 CI 时，在本机执行：

```bash
# JSON Schema
python -c "import json,pathlib,sys; root=pathlib.Path('contracts/events');
[json.loads(p.read_text(encoding='utf-8')) for p in root.glob('*.json')]; print('json ok')"

# YAML（需要 PyYAML）
python -c "import yaml,pathlib; files=list(pathlib.Path('contracts').rglob('*.yaml'));
[yaml.safe_load(p.read_text(encoding='utf-8')) for p in files]; print('yaml ok', len(files))"

# OpenAPI lint（需要 @redocly/cli）
npx --yes @redocly/cli lint contracts/openapi/openapi.yaml
```

PowerShell 也可：`Get-Content -Raw contracts/events/*.json | ConvertFrom-Json`。

CI（WP-00 其余部分）应执行：后端编译、前端检查、**契约校验**。人工添加 runtime↔tool 环依赖时 CI 必须失败（属工程骨架，不在本包）。

## 本包未覆盖

- `/v1/**` OpenAI 兼容代理（M3 / WP-40）
- 通道、钱包、订单、会话、存档、Tool 调用
- Java / TypeScript 代码生成物与 CI workflow（由 WP-00 其他 Agent 或集成 Agent 落地）

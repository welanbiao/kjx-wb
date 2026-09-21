# WP-42 集成说明

状态：`kjx-runtime` 模块内会话/存档/分段披露/Form A 校验/沙箱宿主代理与前台渲染器已落地。未改 `kjx-runtime-api`、`kjx-server`、`kjx-work`、`kjx-tool`、`contracts/`、`docs/`。

## 行为

- 会话与存档校验 `schemaVersion` / `contentVersion` / checksum；存档另有 HMAC `signature`。
- `SessionStateQueryPort` / `SessionStateCommandPort` / `SessionAuthorizationPort` 由 `SessionStateService` 实现。
- 无 entitlement 的阶段下发返回 `gated=true`，布局与节点为空。有 entitlement（含会员 EXPIRED）可继续会话与存档。本模块不读会员 VALID。
- 跨作品、跨账号、跨 session 拒绝。
- 素材签名只走本模块 `WorkRuntimePort`，不改 kjx-work，也不依赖 `kjx-tool`。工具只调 `ToolExecutionPort`。
- Form B/C iframe 使用 `sandbox="allow-scripts allow-forms"`，不给 `allow-same-origin`。postMessage 校验 nonce、origin、方法白名单。宿主回包去掉 token/Cookie 字段。
- 未宣称 iOS / 鸿蒙已验收。

## 待主 Agent 接线

1. 在 `kjx-server` 提供 `WorkRuntimePort`：从 WP-31 已发布包装 `layout` / `tokens` / 按 `stage` 切节点，CDN 签名走现有 `AssetService#sign`，拒绝 `server/`。
2. `RuntimeAccountPort` 已由 `kjx-server` 的 `AppRuntimeAccountAdapter` 接到 `StpKit.APP`。会话接口不再使用 `@ConditionalOnBean`，避免组件扫描阶段把控制器丢掉。作品内容仍等 `WorkRuntimePort`。
3. `ToolExecutionPort` 由 WP-41 提供；缺省返回 `TOOL_UNAVAILABLE`。
4. `RuntimeAiPort` 由 WP-40 提供；缺省 `ai.complete` 返回 `AI_UNAVAILABLE`，不下发 Prompt。
5. 把本模块 Flyway 与 AutoConfiguration 留在 `classpath:db/migration`（禁止 `classpath*:`）。`kjx-server` 已依赖 `kjx-runtime`。
6. 沙箱子域 `{workCode}.game.koujunxin.com` 与 CSP `connect-src 'none'` 由部署/边缘配置，本包只发 origin + nonce。

## 契约缺口（未改 `contracts/`）

OpenAPI 尚未挂会话/存档/宿主代理。本包 HTTP 成功 `code=0`，失败用 HTTP 状态 + `data.errorName`：

| errorName | HTTP | 含义 |
|---|---|---|
| `ENTITLEMENT_REQUIRED` | 403 | 无权益，不下发真实内容 |
| `SESSION_NOT_OWNED` | 403 | 跨账号 |
| `CROSS_WORK` | 403 | 跨作品 |
| `CROSS_SESSION` | 403 | 跨 session / 会话不存在 |
| `SCHEMA_MISMATCH` | 400 | schema/version 不匹配 |
| `CHECKSUM_MISMATCH` | 400 | 存档或状态校验失败 |
| `SAVE_CONFLICT` | 409 | 乐观锁冲突 |
| `METHOD_NOT_ALLOWED` | 403 | 沙箱方法不在白名单 |
| `NONCE_INVALID` / `ORIGIN_REJECTED` | 403 | nonce 或 iframe origin 失败 |
| `UNKNOWN_COMPONENT` | 400 | Form A 非白名单组件 |
| `WORK_PORT_UNAVAILABLE` | 404 | 未接 WorkRuntimePort |

建议路径（与规格 12.3 对齐，尚未写入 openapi）：

- `POST/GET /app/sessions`、`GET/DELETE /app/sessions/{id}`
- `POST /app/sessions/{id}/state`、`GET /app/sessions/{id}/stage`、`GET /app/sessions/{id}/sandbox`
- `GET/POST /app/saves`
- `POST /app/runtime/host`、`POST /app/tools/invoke`

SSE `/app/sessions/{id}/messages` 属 WP-40，本包未实现。

## 版本

Flyway：`V630` 会话，`V631` 存档，`V632` 沙箱票据。预留至 V649。位置 `classpath:db/migration`。

存档 HMAC 环境变量 `kjx.runtime.save-hmac-secret`（缺省 `local-dev-not-a-runtime-key`，**不是生产密钥**）。禁止把密钥打进日志。

## 独立验证

```text
mvn -f backend/pom.xml -pl kjx-modules/kjx-runtime -am test
```

前端（仅 runtime 单测，非 Playwright）：

```text
npx vitest run runtime
```

Testcontainers MySQL 8.4；`disabledWithoutDocker = true`。

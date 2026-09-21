# WP-20 页面-接口映射与剩余集成项

> 范围：仅 `apps/web/**`。契约缺口只记录，不改 `backend/`、`contracts/`、`docs/`。

## 运行模式

| 变量 | 作用 |
|---|---|
| `KJX_API_MODE=mock`（本地默认） | BFF 使用进程内 Mock，字段对齐 OpenAPI |
| `KJX_API_MODE=live`（生产缺省） | BFF 把 `/app/**` 转发到 `KJX_API_ORIGIN`，并用 httpOnly Cookie 换 `Authorization: Bearer` |
| `KJX_API_ORIGIN` | 真实后端根，如 `http://localhost:8080` |
| `NEXT_PUBLIC_ENABLE_MOCK_TOOLS=false` | 隐藏「模拟审核通过」等 Mock 控件 |
| `NEXT_PUBLIC_API_BASE_URL` | 仅健康检查探测 `/actuator/health`，C 端业务请求始终打同源 `/app/**` |

浏览器 JS 不持有 token。登录/注册/刷新响应中的 `data.token` 由 BFF 剥离并写入 `kjx_app_token`（HttpOnly、SameSite=Lax）。

生产构建未配置或误拼 `KJX_API_MODE` 时默认 **live**，不会静默进入 Mock；Mock 工具默认隐藏，E2E 构建显式开启。

## CSRF

OpenAPI 未定义 CSRF。本包采用双提交：

- Cookie `kjx_csrf`（可读）
- 写请求头 `X-CSRF-Token`
- BFF 辅助：`GET /app/auth/csrf`

切 live 后仍走 BFF，不把 CSRF 头转发给后端。

## 页面 → 接口

| 页面 | 主要接口 | 备注 |
|---|---|---|
| `/` 公开入口 | `GET /app/account/profile`（壳层探活） | 游客不拉作品目录 |
| `/register` | `POST /app/auth/send-code`、`POST /app/auth/register` | scene=`register`；成功后 Cookie 会话 |
| `/login` | `POST /app/auth/login` 或 `POST /app/auth/login-by-code`；验证码登录先 `send-code` scene=`login` | 任务页，低装饰 |
| `/reset-password` | `POST /app/auth/send-code` scene=`reset_pwd`、`POST /app/auth/reset-password` | |
| `/me` 个人主页 | `GET /app/account/profile`、`GET /app/member/binding`、`GET /app/member/profile`、`GET /app/member/hosts`、`POST /app/member/binding`、`POST /app/member/binding/appeal` | 五种绑定状态只渲染 API `status` |
| `/works` | `GET /app/works` | OpenAPI 要求 AppBearer |
| `/works/{workCode}` | `GET /app/works/{workCode}`、`GET /app/member/profile`、`POST /app/works/{workCode}/unlock` | 二次确认不计算 `remainCount-1` |
| `/unlocks` | `GET /app/entitlements`、`GET /app/member/profile` | 过期会员仍展示 `entryUrl` |
| `/runtime/{workCode}` | 无（M5 边界） | 占位页，不实现 Runtime |
| 壳层登出 | `POST /app/auth/logout` | 清除会话 Cookie |
| Mock 审核 | `POST /app/_mock/approve-binding` | 仅 mock 模式；E2E「审核后刷新」 |
| Mock 重置 | `POST /app/_mock/reset` | 仅测试 |

统一错误展示：接口 `msg` + `code` + `requestId`。`1010 ALREADY_UNLOCKED` 按成功进入处理。

## 字段纪律

- 会员 `status` / `availableCount` / `unlockedCount` / `remainCount` 只展示接口值。
- 前端不做 `floor(days/30)`、不做 `months*30`、不根据 `expireAt` 判断有效。
- 新解锁是否允许由后端错误码（1005/1006/1007/1009）或已返回的 `member.status` 决定按钮禁用，不在本地推算额度。

## 契约缺口（不改合同，待集成 Agent）

1. **CSRF**：OpenAPI 无 CSRF 头/接口；BFF 自行双提交。
2. **token 落点**：`RegisterData`/`LoginData` 要求 body 含 `token`；浏览器响应已被 BFF 删除该字段。
3. **游客浏览**：docs/04、05 允许游客看作品列表/详情；OpenAPI `GET /app/works` 与详情为 `AppBearer`。前端按契约先登录。
4. **图形验证码**：`send-code` 需要 tianai-captcha 的 `captchaId`/`captchaData`；无前端挑战接口。当前 Mock 复选框占位。
5. **账号与安全页**：注销撤销 path 已挂入总 OpenAPI，但注销/撤销、设备管理、改密尚未做独立页面。
6. **刷新令牌**：`POST /app/auth/refresh` Mock/BFF 已转发，UI 无主动刷新按钮。
7. **灰度分桶**：Mock 列表对 `GRAY` 未实现 CRC32 分桶，live 依赖后端。
8. **host 三级取值**：绑定 `hostCode` 可选；本包在仅一个主播时隐藏下拉，未实现投放链接解析。
9. **Runtime/演示模式**：`entryUrl` 指向 `/runtime/{workCode}` 占位；演示模式、通道、计费未做。

## 浏览器与移动端门禁

- 明确构建基线：Chrome/Edge/Firefox 111+、Safari 16.4+。
- CSS 使用 `100vh` 回退 + `100svh`，并为 `overflow-x: clip` 提供 `hidden` 回退。
- Playwright 在桌面 Chrome 与 Pixel 5 触控仿真上各跑 3 条旅程，共 6 条。
- iOS Safari/WebKit 与鸿蒙 ArkWeb 仍需对应引擎和真机验收，当前不能宣称已覆盖。

## 切 live 清单

1. `KJX_API_MODE=live`
2. `KJX_API_ORIGIN` 指向 kjx-server（经 Nginx 亦可）
3. `NEXT_PUBLIC_ENABLE_MOCK_TOOLS=false`
4. 后端须接受 BFF 的 Bearer，并允许 BFF 源站点的 Cookie 会话由本层持有
5. 接入真实 tianai-captcha 后再打开验证码发送
6. 绑定审核改为后台 WP-21，不再使用 `/app/_mock/**`

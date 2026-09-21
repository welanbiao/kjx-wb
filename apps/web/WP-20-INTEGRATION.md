# WP-20 页面-接口映射与剩余集成项

> 范围：仅 `apps/web/**`。契约缺口只记录，不改 `backend/`、`contracts/`、`docs/`。
> 并行 WP-13 可能改 entitlement/unlock 字段；本包严格按当前 OpenAPI 实现，不猜测新字段。

## 运行模式

| 变量 | 作用 |
|---|---|
| `KJX_API_MODE=mock`（本地可配） | BFF 使用进程内 Mock，字段对齐当前 OpenAPI |
| `KJX_API_MODE=live`（生产缺省） | BFF 把 `/app/**` 转发到 `KJX_API_ORIGIN`，HttpOnly Cookie 换 `Authorization: Bearer` |
| `KJX_API_ORIGIN` | 真实后端根，如 `http://localhost:8080`。live 未配置时 BFF 返回 500，不会静默 Mock |
| `NEXT_PUBLIC_ENABLE_MOCK_TOOLS=true` | 显示「模拟审核通过」，并允许 `window.__KJX_CAPTCHA_TEST_DOUBLE__` 注入滑块测试替身 |
| `NEXT_PUBLIC_API_BASE_URL` | 仅健康检查探测 `/actuator/health`，C 端业务请求始终打同源 `/app/**` |

未配置或误拼 `KJX_API_MODE` 时：`NODE_ENV=production` → **live**，否则本地 → mock。生产不得静默 Mock。

浏览器 JS 不持有 token。登录/注册/刷新响应中的 `data.token` 由 BFF 剥离并写入 `kjx_app_token`（HttpOnly、SameSite=Lax）。禁止写入 localStorage / sessionStorage / JS 可读 Cookie。

测试工具只有显式 `NEXT_PUBLIC_ENABLE_MOCK_TOOLS=true` 才出现。生产必须省略或设 `false`。

## CSRF

OpenAPI 未定义 CSRF。本包采用双提交：

- Cookie `kjx_csrf`（可读）
- 写请求头 `X-CSRF-Token`
- BFF 辅助：`GET /app/auth/csrf`

切 live 后仍走 BFF。CSRF 头、浏览器 Cookie、`Set-Cookie`、`Authorization`、`Content-Encoding`/`Content-Length`/`Transfer-Encoding` 等传输头不会转发给后端，也不会从上游回传到浏览器。

## 图形验证码（WP-10）

### 依赖 / 许可判断

优先评估了 tianai 官方 web SDK：

- 源码：Gitee `tianai/tianai-captcha-web-sdk` / GitHub `tianaiyouqing/captcha-web-sdk`，许可证 **MulanPSL-2.0**（许可本身清晰）。
- 官方接入模型是 `requestCaptchaDataUrl` + `validCaptchaUrl`（`/gen` + `/check`），由 SDK 自行 POST 轨迹。
- 当前冻结契约是 `GET /app/auth/captcha` 取挑战，再把 **ImageCaptchaTrack** 作为 `send-code` 的 `captchaData` 回传；OpenAPI **没有** `/check`。
- 历史产物依赖 jQuery + `tac.min.js` UMD；npm 上的 `captcha-web-sdk@1.0.x` 与后端 tianai-captcha **1.5.2** 版本配对不明确。
- Playwright 下官方弹层难以稳定提供可注入替身。

因此 **未安装官方 SDK**，在 `apps/web` 实现最小 SLIDER：鼠标 + 触控 + 键盘（`role="slider"`），采集冻结字段：

`captchaId` = 挑战 `id`；`captchaData` = `{ bgImageWidth, bgImageHeight, templateImageWidth, templateImageHeight, startTime, stopTime, trackList[{x,y,t,type}] }`。

不把答案、percentage、服务端缓存写入 GET `/app/auth/captcha`。错误或过期后刷新挑战；send-code 只传当前冻结的 `captchaId`/`captchaData`。校验失败后 Mock 与 live 均一次性消费挑战。

测试替身：仅当 `NEXT_PUBLIC_ENABLE_MOCK_TOOLS=true` 时读取 `window.__KJX_CAPTCHA_TEST_DOUBLE__`。生产构建关闭该变量后，即使页面被注入该对象也不会走替身。生产路径不得绕过滑块。

Mock 滑块目标比例只存在 Mock 进程内，不在挑战 JSON 中。E2E 替身使用同一测试常量，不等于把缓存暴露给普通客户端。

## 页面 → 接口

| 页面 | 主要接口 | 备注 |
|---|---|---|
| `/` 公开入口 | `GET /app/account/profile`（壳层探活） | 游客不拉作品目录 |
| `/register` | `GET /app/auth/captcha`、`POST /app/auth/send-code`、`POST /app/auth/register` | scene=`register`；成功后 Cookie 会话 |
| `/login` | `POST /app/auth/login` 或 `POST /app/auth/login-by-code`；验证码登录先 captcha + send-code scene=`login` | 任务页，低装饰 |
| `/reset-password` | captcha + send-code scene=`reset_pwd`、`POST /app/auth/reset-password` | |
| `/me` | `GET /app/account/profile`、`PUT /app/account/profile`、`GET /app/member/binding`、`GET /app/member/profile`、`GET /app/member/hosts`、`POST /app/member/binding`、`POST /app/member/binding/appeal` | 五种绑定状态只渲染 API `status`；PENDING 每 8s 轮询 + 手动刷新 |
| `/me/security` | `PUT /app/account/password`、`POST /app/auth/refresh`、`GET /app/account/devices`、`DELETE /app/account/devices/{id}`、captcha + send-code scene=`cancel`、`POST /app/account/cancel`、`POST /app/account/cancel/revoke` | 设备列表不展示 token |
| `/works` | `GET /app/works` | OpenAPI 要求 AppBearer；可传 pageNo/pageSize/workType/unlocked |
| `/works/{workCode}` | `GET /app/works/{workCode}`、`GET /app/member/profile`、`POST /app/works/{workCode}/unlock` | 二次确认不计算 `remainCount-1`；1010 当成功进入 |
| `/unlocks` | `GET /app/entitlements`、`GET /app/member/profile` | 过期会员仍展示 `entryUrl`；下架已购可进入 |
| `/runtime/{workCode}` | 无（M5 边界） | 占位页，不实现 Runtime |
| 壳层登出 | `POST /app/auth/logout` | 清除会话 Cookie |
| Mock 审核 | `POST /app/_mock/approve-binding` | 仅 mock + mock tools；E2E「审核后刷新」 |
| Mock 重置 | `POST /app/_mock/reset` | 仅测试 |

统一错误展示：接口 `msg` + `code` + `requestId`。`1001/1002/1003/1004/1014/1090` 使用契约文案。`1010 ALREADY_UNLOCKED` 按成功进入处理。

## 字段纪律

- 会员 `status` / `availableCount` / `unlockedCount` / `remainCount` 只展示接口值。
- 前端不做 `floor(days/30)`、不做 `months*30`、不根据 `expireAt` 判断有效。
- 新解锁按钮仅按接口已返回的 `member.status`（EXPIRED / NOT_STARTED）禁用，或在提交后展示后端错误码（1005/1006/1007/1009）。不在本地推算额度或 GRAY 分桶。
- send-code 不新增字段；captchaData 不附加 percentage / 答案。

## 契约缺口（不改合同，待集成 Agent）

1. **CSRF**：OpenAPI 无 CSRF 头/接口；BFF 自行双提交。
2. **token 落点**：`RegisterData`/`LoginData` 要求 body 含 `token`；浏览器响应已被 BFF 删除该字段。
3. **游客浏览**：docs/04、05 允许游客看作品列表/详情；OpenAPI `GET /app/works` 与详情为 `AppBearer`。前端按契约先登录。
4. **图形验证码 SDK**：官方 web SDK 走独立 `/check`，与冻结 `captchaData` 模型不兼容；本包用本地 SLIDER。若后续契约增加 check 接口，再评估接入官方 SDK。
5. **注销发码手机号**：`POST /app/auth/send-code` 仍要求明文 `phone`；`GET /app/account/profile` 只有 `phoneMask`。安全页让用户重填手机号，未猜测 session 免填字段。
6. **host 三级取值**：绑定 `hostCode` 可选；仅一个主播时隐藏下拉，未实现投放链接解析。
7. **灰度分桶**：Mock 将 GRAY 作品对登录用户可见；live 依赖后端 CRC32，前端不计算。
8. **WP-13 并行**：unlock/entitlement 若增字段，本包不会预先消费。当前使用 `UnlockResult.alreadyUnlocked/entryUrl/remainCount/unlockedCount` 与 `EntitlementItem.entryUrl/workStatus`。
9. **Runtime/演示模式**：`entryUrl` 指向 `/runtime/{workCode}` 占位；演示模式、通道、计费未做。
10. **tianai 轨迹**：`startTime`/`stopTime` 使用 ISO-8601（Jackson `java.util.Date` 默认可解析）。`trackList.type` 为大写 `DOWN`/`MOVE`/`UP`。提交前把用户真实路径加密到不少于 12 个点、相邻步长不超过 45px，且起止间隔必须大于 300ms；短于 300ms 的拖动会提示重试，不会改写时间戳。百分比仍取终点 x / 展示宽度。

## 浏览器覆盖声明

- 明确构建基线：Chrome/Edge/Firefox 111+、Safari 16.4+。
- 本轮 Playwright **仅桌面 Chrome** 跑主旅程：注册 → 图形验证码 → 绑定 → Mock 模拟审核 → 刷新 → 解锁 → 我的解锁。
- 本轮 **不** 覆盖 iOS Safari/WebKit、鸿蒙 ArkWeb 或真机；**不** 宣称 Pixel/触控仿真即移动端验收。
- CSS 使用 `100vh` 回退 + `100svh`，`overflow-x: clip` 提供 `hidden` 回退。
- 无障碍：跳过链接、可见 `:focus-visible`、控件最小 44px 触控、对话框焦点循环、滑块键盘操作、`prefers-reduced-motion`。

## 切 live 清单

1. `KJX_API_MODE=live`（或生产缺省）
2. `KJX_API_ORIGIN` 指向 kjx-server（经 Nginx 亦可）
3. `NEXT_PUBLIC_ENABLE_MOCK_TOOLS` 不设或 `false`
4. 后端须接受 BFF 的 Bearer；Cookie 会话由 BFF 持有
5. 绑定审核改为后台 WP-21，不再使用 `/app/_mock/**`
6. 确认 live 对 ImageCaptchaTrack 的 `startTime`/`stopTime`/`trackList.type` 大小写与本包一致

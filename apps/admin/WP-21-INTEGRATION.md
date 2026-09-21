# WP-21 集成说明（RuoYi 管理后台业务页）

> 范围：仅 `apps/admin/**`。契约或后端缺口只记录，不改 `backend/`、`contracts/`、`docs/`、`apps/web/**`。
> 基线提交：`5806635`。未提交、未推送 Git。

## 运行模式

| 变量 | 作用 |
|---|---|
| `VITE_API_MODE=mock` | 显式进程内 Mock，字段对齐当前 OpenAPI。本地 `.env.example` 默认此项 |
| `VITE_API_MODE=live` | 请求真实 `/admin-api/**`（开发时 Vite 可把 `/admin-api` 代理到 `VITE_API_BASE_URL`） |
| 生产构建未设置 | 默认为 **live**，不会静默 Mock |
| `VITE_API_BASE_URL` | live 根地址；健康检查始终探测 `{base}/actuator/health` |
| `VITE_BASE` | 静态资源与 History 前缀，`/` 或 `/admin/` |

Mock 账号（**不是真实凭据**）：`host-a` / `host-b` / `operator` / `superadmin`，密码为 `mock-` 前缀。

浏览器把会话放在 `sessionStorage`（含 token）。UI 与 `console` 不打印手机号、token、验证码。导出使用 `createObjectURL`，下载后 `revokeObjectURL`。

## 页面清单

| 路由 | 角色 | 主要接口 |
|---|---|---|
| `/login` | 公开 | Mock：`POST /admin-api/auth/login`。Live：见下方缺口 |
| `/dashboard` | 已登录 | 无 |
| `/health` | 已登录 | `GET /actuator/health` |
| `/self/host` | HOST | `GET /admin-api/kjx/self/host` |
| `/self/members` | HOST | `GET /admin-api/kjx/self/member/page`、`GET .../export` |
| `/self/members/new` | HOST | `POST /admin-api/kjx/self/member` |
| `/self/members/:id` | HOST | 列表扫描详情 + `GET .../member/{id}/code` |
| `/self/members/:id/edit` | HOST | `PUT /admin-api/kjx/self/member/{id}`（`reason` 必填） |
| `/self/bindings` | HOST 只读 | `GET /admin-api/kjx/self/binding/page` |
| `/self/unlock-records` | HOST | `GET /admin-api/kjx/unlock-record/page`（后端按部门过滤） |
| `/ops/hosts` | OPERATOR / superadmin | `GET/POST /admin-api/kjx/host` |
| `/ops/hosts/new`、`/ops/hosts/:id` | 同上 | 创建必填 `adminUserId` + `reason` |
| `/ops/members` | 同上 | `GET /admin-api/kjx/member/page` |
| `/ops/members/new` | 同上 | `POST /admin-api/kjx/member`（`deptId` + `reason`） |
| `/ops/members/:id` | 同上 | `GET`、`POST .../recalc` |
| `/ops/members/:id/edit` | 同上 | `PUT /admin-api/kjx/member/{id}` |
| `/ops/bindings` | 同上 | page / approve / reject / force-unbind |
| `/ops/appeals` | 同上 | page / handle |
| `/ops/works`、`/ops/works/new`、`/ops/works/:id` | 同上 | WP-12 CRUD + `status` 迁移 |
| `/ops/entitlements` | 同上 | `POST /admin-api/kjx/member/{id}/entitlement` |
| `/ops/unlock-records` | 同上 | page + **export**（HOST 禁用） |
| `/forbidden` | 已登录但前端拒绝 | 直输 URL 也会落到这里。**不是授权** |

写接口统一带 `Authorization`（已登录）、`X-Request-Id`、`X-Idempotency-Key`。错误展示 `msg` + `requestId`。

## 权限矩阵（前端隐藏 / 直输拒绝）

后端才是权限边界。HOST 自助与运营路径分离，与 WP-11 一致。

| 能力 | HOST | OPERATOR | superadmin |
|---|---|---|---|
| 我的主播 / 我的会员 / 绑定状态 | 是 | 否 | 否 |
| 本部门解锁记录 | 是 | 否（走运营页） | 否（走运营页） |
| 主播 CRUD（含 adminUserId） | 否 | 是 | 是 |
| 跨部门会员 / 重算 | 否 | 是 | 是 |
| 绑定通过 / 驳回 / 强制解绑 | 否 | 是 | 是 |
| 申诉处理 | 否 | 是 | 是 |
| 作品 CRUD / 发布 / 下架 / 灰度 | 否 | 是 | 是 |
| 人工权益 ADD/REMOVE | 否 | 是 | 是 |

`LoginHelperDeptAccessPort` 语义：`superadmin`/`OPERATOR` 视为 ops；纯 `HOST` 才走自助。

## 字段纪律

- 录入/编辑：`subscribedMonths`、`subscribedDays`、`expireAt` 独立必填。
- 前端 `patchMemberTerm` 改一项不会改另外两项。
- 日期展开为 `YYYY-MM-DDT23:59:59.999+08:00`，不用月/天推到期。
- `availableCount` 只展示接口值。Mock 服务端用 `floor(days/30)` 模拟派生，UI 不做该计算。
- 作品状态按钮对齐 WP-12：`DRAFT→PENDING_REVIEW→PUBLISHED|GRAY|REJECTED`，`PUBLISHED/GRAY↔UNPUBLISHED`。
- 权益调整 **只发** `{ action, workCode, reason }`（`entitlement-adapter.ts`）。

## 测试结果

在 `apps/admin`：

| 命令 | 结果 |
|---|---|
| `npm run lint` | 通过 |
| `npm run typecheck` | 通过 |
| `npm run test` | 通过（11 个文件 / 37 条，含 HOST A 无 B 数据、OPERATOR 403 自助、reason、月/天不联动、审核参数、作品状态、解锁导出禁 HOST） |
| `npm run build` | 通过 |
| `npm run test:e2e` | 通过（3 条 Playwright，Mock，无真实后台凭据；使用 apps 工作区已有 Playwright，未改 lockfile） |

## 契约 / 后端缺口（待主 Agent / WP-13）

1. **后台登录不在 OpenAPI**。Live 用 typed adapter 调 `/admin-api/auth/login` 与 `/admin-api/system/user/getInfo`，并兼容 RuoYi `code=200`。仓库内未见该 Controller；且 `DualDomainSaTokenConfig` 对全部 `/admin-api/**` 要求已登录，**login 自身可能 401**。需 WP-01/集成放行登录与验证码。
2. **HOST 无 `GET /admin-api/kjx/self/member/{id}`**。详情页用分页扫描，刷新可能找不到非首页记录。建议补 GET 或允许 `id` 查询。
3. **WP-13 并行**：`POST /member/{id}/entitlement` 与 `GET /unlock-record/page` 已在 OpenAPI。WP-13 工作区已出现对应 Controller，调整请求仍只用冻结字段 `{ action, workCode, reason }`。`GET /unlock-record/export` 已做运营导出按钮；**HOST 不得调用**。总 OpenAPI 已 `$ref` 该路径。
4. **HOST 调 unlock-record page**：查询已按本部门收窄。导出和人工调整仍返回 403。总 OpenAPI 已登记 export。
5. **作品乐观锁**：WP-12 实现可能 409 `WORK_UPDATE_CONFLICT`，总 OpenAPI 的 PUT work 未列 409。前端展示 `msg` + `requestId`。
6. **验证码 / clientId**：RuoYi 登录常要 captcha。未硬编码真实 client 密钥；live body 仅 `username/password/grantType/tenantId`。
7. **PUBLISHER / FINANCE**：识别角色但不开放本 WP 业务按钮。

## 风险

- 前端路由守卫可被绕过，任何敏感操作必须以后端 401/403 为准。
- Mock 与 live 行为在登录信封（code 0 vs 200）上不同，切 live 前先打通 RuoYi 登录。
- 未做真机读屏与 320px 全量走查；样式按 768/桌面做了侧栏折叠、表格横滑、44px 触控、`prefers-reduced-motion`。
- 生产包含 Mock 实现（仅 `VITE_API_MODE=mock` 时走），体积偏大。

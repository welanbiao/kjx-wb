# 叩君芯管理端（WP-21）

Vue 3 + Vite + TypeScript + Element Plus。业务页对齐 `contracts/openapi` 的 admin-self / admin-ops。

**当前目录不是 RuoYi-Vue-Plus 源码。** 认证在 live 模式经 typed adapter 调用 `/admin-api/auth/login` 与 `/admin-api/system/user/getInfo`；缺口见 `WP-21-INTEGRATION.md`。

## 本地启动

```bash
cd apps
npm install
cp admin/.env.example admin/.env.local
npm run dev -w @kjx/admin
```

默认 `VITE_API_MODE=mock`，不依赖真实后台凭据。Mock 账号：`host-a` / `host-b` / `operator` / `superadmin`，密码为 `mock-` 前缀。

切真实 API：

```bash
VITE_API_MODE=live
VITE_API_BASE_URL=http://localhost:8080
```

## 公共路径

由 `VITE_BASE` 控制。直连开发用 `/`，Nginx 挂载用 `/admin/`。

## 脚本

| 命令 | 作用 |
|---|---|
| `npm run lint` | ESLint |
| `npm run typecheck` | `vue-tsc --noEmit` |
| `npm run test` | Vitest |
| `npm run build` | 生产构建 |
| `npm run test:e2e` | Playwright（Mock，无真实凭据） |

## 契约路径

- HOST 自助：`/admin-api/kjx/self/**`
- 运营：`/admin-api/kjx/**`
- 写接口带 `X-Idempotency-Key` 与 `X-Request-Id`
- 关键修改收集 `reason`

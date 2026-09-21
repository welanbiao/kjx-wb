# C 端会员前台（WP-00C 骨架）

Next.js 16 + React 19 + TypeScript + Tailwind CSS 4。本目录只提供可启动空页与 API 客户端占位，业务页面由 **WP-20** 实现。

## 本地启动

```bash
cd apps
npm install
npm run dev -w @kjx/web
```

默认 http://localhost:3000 。复制 `.env.example` 为 `.env.local` 后按需修改。

## 脚本

| 命令 | 作用 |
|---|---|
| `npm run lint` | ESLint |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run test` | Vitest |
| `npm run build` | 生产构建 |

## 认证约定

- 使用后端 / BFF 下发的 **httpOnly Cookie**，请求带 `credentials: "include"`。
- **禁止**把 Sa-Token 或 refresh token 写入 Web Storage。
- 业务判断（会员状态、额度、解锁）以后端为准，前端不自行换算月数/天数。

## 契约路径

C 端只调用 `/app/**`。旧 `/api/**` 已废止。

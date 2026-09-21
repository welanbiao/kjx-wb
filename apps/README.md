# kjx-apps

叩君芯会员产品平台前端工作区（WP-00C 骨架）。

| 应用 | 目录 | 技术栈 | 后续工作包 |
|---|---|---|---|
| C 端会员前台 | [`web/`](./web) | Next.js 16 + React 19 + Tailwind 4 | WP-20 |
| 管理端壳 | [`admin/`](./admin) | Vue 3 + Vite + Element Plus | WP-21，后续接入 RuoYi-Vue-Plus v5.2.3 |

## 安装与检查

管理端经 Nginx 挂到 `/admin/` 时，把 `VITE_BASE=/admin/` 写入 `admin/.env.local`；直连 `localhost:3001` 保持默认 `/`。详见 [`admin/README.md`](./admin/README.md)。

```bash
cd apps
npm ci
npm run static-check
npm run lint
npm run typecheck
npm run test
```

无依赖时只跑静态门禁：

```bash
node scripts/static-check.mjs
```

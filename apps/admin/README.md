# 叩君芯管理端壳（WP-00C）

这是 **Vue 3 + Vite + TypeScript + Element Plus** 的最小可运行管理端骨架，用于占位路由、登录页和健康检查。

## 与 RuoYi-Vue-Plus 的关系（必读）

**当前目录不是 RuoYi-Vue-Plus 源码，也不会伪造、裁剪或二次封装 plus-ui。**

后续 **WP-21** 将接入官方 **RuoYi-Vue-Plus v5.2.3** 的 `plus-ui`（Vue 3 + Element Plus + Sa-Token 后台），再叠加叩君芯业务页：

- 主播部门扩展
- HOST 会员录入（月数 / 天数独立必填）
- 绑定审核与申诉
- 作品管理、解锁记录

在那次集成完成前，本壳只提供空页面与 `/admin-api` 请求占位，不能当作 RuoYi 后台使用。

## 本地启动

```bash
cd apps
npm install
npm run dev -w @kjx/admin
```

默认直连 http://localhost:3001/ 。复制 `.env.example` 为 `.env.local` 后按需修改。

## 公共路径（Vite `base` / Router history）

由 `VITE_BASE` 控制，Vite 会写入 `import.meta.env.BASE_URL`，路由使用 `createWebHistory(import.meta.env.BASE_URL)`。

| 场景 | `VITE_BASE` | 访问地址 |
|---|---|---|
| 直连开发（默认） | `/` | http://localhost:3001/ |
| Nginx 反代挂载 | `/admin/` | http://127.0.0.1/admin/ |

经 Docker Compose `proxy` + `frontend` 或宿主机 Nginx（`deploy/nginx/dev.conf`）时，**不要改 Nginx 去剥掉 `/admin` 前缀**，应让 Vite 自己认这个 base：

```bash
# .env.local 或启动前导出
VITE_BASE=/admin/
npm run dev -w @kjx/admin
```

生产构建同样：

```bash
VITE_BASE=/admin/ npm run build -w @kjx/admin
```

未设置或设为 `/` 时，直连开发与 `npm run build` 仍走站点根路径，不会被强制加上 `/admin/`。

## 脚本

| 命令 | 作用 |
|---|---|
| `npm run lint` | ESLint |
| `npm run typecheck` | `vue-tsc --noEmit` |
| `npm run test` | Vitest |
| `npm run build` | 生产构建 |

## 契约路径

管理端只调用 `/admin-api/**`（HOST 自助为 `/admin-api/kjx/self/**`）。登录态由后续 RuoYi-Vue-Plus 接管，本骨架不把 token 写入本地存储。

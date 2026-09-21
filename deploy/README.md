# 叩君芯 · 本地部署（M1）

M1 开发栈只有 **MySQL 8.4、Redis 8、可选 Nginx / 后端 / 前端**。不要在本目录加入 Higress、ClickHouse 或观测栈。

## 文件

| 路径 | 说明 |
|---|---|
| `compose/docker-compose.dev.yml` | 开发 Compose。默认只起 MySQL + Redis |
| `nginx/nginx.conf` | Nginx 主配置（含 websocket `map`） |
| `nginx/templates/dev.conf.template` | Compose `proxy` profile 使用，envsubst 渲染 |
| `nginx/dev.conf` | 本机 Nginx 可直接 `include` 的 server 段 |

## 前置

- Docker Compose v2
- 仓库根目录已有 `.env`（从 `.env.example` 复制，勿提交）

时区、MySQL、JVM、容器统一 `Asia/Shanghai`。MySQL 字符集 `utf8mb4` / `utf8mb4_0900_ai_ci`，慢查询 1s。Redis 开启 AOF 并要求密码。端口只发布到 `127.0.0.1`。

## 启动

在仓库根目录：

```bash
# 仅中间件（日常开发默认）
docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml up -d

# 增加 Nginx 开发反代（反代到宿主机 8080/3000/3001）
docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml --profile proxy up -d

# 可选：容器跑后端 / 前端。对应源码、Dockerfile 约定与 lockfile 未就绪时会失败，这是预期行为
docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml --profile backend --profile frontend --profile proxy up -d
```

停止：

```bash
docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml --profile proxy --profile backend --profile frontend down
```

数据在 Docker volume `kjx_mysql_data` / `kjx_redis_data`。需要空库时再 `down -v`，不要在业务库上对 Flyway 使用 `baseline-on-migrate` 掩盖状态错误。

## 路由

开发 Nginx（`http://127.0.0.1/`）按最长前缀匹配，避免 `/admin` 吞掉 `/admin-api`：

| 路径 | 目标 | 说明 |
|---|---|---|
| `/app/`、`/app` | 后端 `:8080` | C 端 API，`loginType=app` |
| `/admin-api/`、`/admin-api` | 后端 `:8080` | 后台 API，`loginType=admin` |
| `/actuator/` | 后端 `:8080` | 健康检查；生产应收紧 |
| `/admin/`、`/admin` | 管理端 `:3001` | 后台 UI。走 Nginx 时 Vite `base` 应为 `/admin/`；骨架期可直连 `:3001` |
| `/` | 前台 `:3000` | Next.js 会员前台 |

`/v1/**`（OpenAI 兼容代理）从 M3 才启用，本配置不反向代理该前缀。

## Profile 约定

| Profile | 服务 | 何时开启 |
|---|---|---|
| （默认） | `mysql`、`redis` | 始终 |
| `proxy` | `nginx` | 需要统一 80 端口时 |
| `backend` | `backend` | 用容器跑 `kjx-server`，且 `backend/` 已可 `mvn spring-boot:run` |
| `frontend` | `web`、`admin` | 容器跑前后台，且 `apps/package-lock.json`（workspace）或各包 lockfile 已提交 |

`backend` / `frontend` 通过服务名访问 `mysql` / `redis`。本机进程则使用 `.env` 里的 `127.0.0.1`。

无 lockfile 时 `frontend` profile **会失败退出**，不会静默 `npm install` 后假装构建成功。日常开发在宿主机：

```bash
cd apps
npm ci
npm run dev -w @kjx/web
npm run dev -w @kjx/admin
```

两个 frontend 容器共享 `apps/` 绑定，并行 `npm ci` 可能争用 `node_modules`；需要容器冒烟时建议先在宿主机装好依赖，或只起其中一个。

## 校验

```bash
docker compose --env-file .env.example -f deploy/compose/docker-compose.dev.yml config --quiet

docker run --rm \
  -e BACKEND_UPSTREAM=host.docker.internal:8080 \
  -e WEB_UPSTREAM=host.docker.internal:3000 \
  -e ADMIN_UPSTREAM=host.docker.internal:3001 \
  -v "${PWD}/deploy/nginx/nginx.conf:/etc/nginx/nginx.conf:ro" \
  -v "${PWD}/deploy/nginx/templates/dev.conf.template:/etc/nginx/templates/default.conf.template:ro" \
  nginx:1.27-alpine nginx -t
```

Windows PowerShell 把 `${PWD}` 换成 `$((Get-Location).Path)`。

## 与后续工作包

- WP-01：RuoYi Flyway baseline、Sa-Token 双域、Redisson、Actuator。
- WP-51：生产 Compose、备份恢复、Higress（M3 起）与 ClickHouse（分析投影，不得用于权限判断）。

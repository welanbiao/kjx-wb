# 叩君芯会员产品平台

单仓库、单 Spring Boot 进程（`kjx-server`）。M1 只跑 MySQL 8.4、Redis 8、Nginx、后端和前后台；**不引入 Higress / ClickHouse / 观测栈**。

> 发生冲突时以 `docs/13-统一架构DDL与接口契约基线.md` 为准。需要改冻结基线时先提 ADR，不要在业务工作包里直接改路径、错误码、DDL 语义或事务边界。

## 必读文档

| 优先级 | 文档 | 用途 |
|---|---|---|
| 1 | [`docs/13-统一架构DDL与接口契约基线.md`](docs/13-统一架构DDL与接口契约基线.md) | 架构边界、DDL、API、事务、幂等、事件 |
| 2 | [`docs/14-多Agent并行开发实施方案.md`](docs/14-多Agent并行开发实施方案.md) | 工作包、文件所有权、Gate、合并顺序 |
| 3 | [`docs/11-M1接口契约与业务联动.md`](docs/11-M1接口契约与业务联动.md) | M1/M2 请求响应细节 |
| 4 | [`docs/09-M1数据库设计与项目骨架.md`](docs/09-M1数据库设计与项目骨架.md) | M1/M2 完整建表语句 |
| 5 | [`docs/08-系统架构设计.md`](docs/08-系统架构设计.md) | 技术栈与部署 |
| — | [`deploy/README.md`](deploy/README.md) | Compose / Nginx 本地启动 |

旧 `/api/**` 路径已废止，不提供双轨。时间统一 `Asia/Shanghai`。密钥只进环境变量，不进 Git。

## 仓库目录

```text
/
├── docs/                 # 设计文档（只读基线，非本工作包代码）
├── contracts/            # OpenAPI、错误码、事件 Schema
│   ├── openapi/
│   ├── schemas/
│   ├── events/
│   └── errors.yaml
├── backend/              # 唯一后端：Maven 多模块
│   ├── pom.xml
│   ├── ruoyi-common/
│   ├── kjx-modules/      # 自研业务 jar，禁止跨模块访问实现包
│   └── kjx-server/       # 唯一启动模块：装配、配置、Flyway
├── apps/
│   ├── web/              # Next.js 会员前台
│   └── admin/            # RuoYi / Vue 管理后台，路由前缀 /admin
├── tests/                # 跨模块 E2E / 压测（WP-51）
├── deploy/               # Compose、Nginx、部署说明
└── .github/workflows/    # CI
```

## 启动顺序（M1）

1. 复制环境变量：`copy .env.example .env`（PowerShell）或 `cp .env.example .env`。
2. 启动 MySQL 8.4 与 Redis 8（见下方命令）。空库由 Flyway 迁移，不要手工导 SQL 再跑 baseline。
3. 启动 `kjx-server`（Flyway 随进程执行）。
4. 启动前台 `apps/web` 与后台 `apps/admin`。
5. 可选：启动 Nginx 开发反代，统一入口 `http://127.0.0.1`。

健康检查：MySQL `mysqladmin ping`；Redis `PING`；后端 `/actuator/health`。

### 常用命令

```bash
# 1) 中间件（默认只起 MySQL + Redis，绑定 127.0.0.1）
docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml up -d

# 可选：开发反代
docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml --profile proxy up -d

# 可选：容器内后端 / 前端（骨架未就绪时不要开这些 profile）
docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml --profile backend up -d
docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml --profile frontend up -d

# 2) 后端
cd backend
mvn -B -ntp test
mvn -pl kjx-server spring-boot:run

# 3) 前台 / 后台（npm workspaces 在 apps/；有 lockfile 后用 ci，不要无锁安装冒充可复现构建）
cd apps
npm ci
npm run dev -w @kjx/web
npm run dev -w @kjx/admin

# 4) 契约
python .github/scripts/validate-contracts.py
```

| 入口 | 地址 |
|---|---|
| Nginx（可选） | `http://127.0.0.1/` |
| 会员前台 | `http://127.0.0.1:3000/` |
| 管理后台 | `http://127.0.0.1:3001/`（直连）或 Nginx `http://127.0.0.1/admin/` |
| C 端 API | `http://127.0.0.1:8080/app/**` |
| 后台 API | `http://127.0.0.1:8080/admin-api/**` |
| 健康检查 | `http://127.0.0.1:8080/actuator/health` |

Nginx 将 `/app`、`/admin-api` 转到后端，`/admin` 转到管理端，其余转到前台。详见 `deploy/README.md`。

## Agent 文件所有权

业务 Agent 只改自己的目录。公共文件由 WP-00 / 集成 Agent 修改；越界改动提交补丁说明，不要直接重构。

| 路径 | 工作包 |
|---|---|
| `contracts/**`、根构建与 CI、`README.md`、`.gitignore`、`.env.example` | WP-00 |
| `backend/pom.xml`、各空模块 POM、ArchUnit/Enforcer 门禁 | WP-00 |
| `apps/web`、`apps/admin` 初始骨架 | WP-00 |
| `deploy/compose/**`、`deploy/nginx/**` 开发骨架 | WP-00（运行时细化归 WP-01，发布归 WP-51） |
| `backend/ruoyi-common/**`、`kjx-server` 基础配置、Flyway V1/RuoYi baseline | WP-01 |
| `kjx-runtime-api/**`、幂等、Outbox、`contracts/events/**`、V100–V149 | WP-02 |
| `kjx-account/**`、V200–V249 | WP-10 |
| `kjx-member/**` 中 member/binding/appeal、V300–V329 | WP-11 |
| `kjx-work/**` 基础目录、V400–V419 | WP-12 |
| 解锁与权益、V330–V349 | WP-13 |
| `apps/web/**` 业务页面 | WP-20 |
| `apps/admin/**` 业务页面 | WP-21 |
| `kjx-channel/**`、`kjx-billing/**`、V500–V549 | WP-30 |
| 作品包/内容/素材、V420–V449 | WP-31 |
| `kjx-ai/**` | WP-40 |
| `kjx-tool/**` | WP-41 |
| `kjx-runtime/**` | WP-42 |
| `kjx-log/**`、V700–V749 | WP-50 |
| `tests/**`、生产部署与恢复 | WP-51 |

Flyway 版本段独占，跨段迁移交给集成 Agent。

仅集成 Agent 可改：根 POM / 模块聚合 POM、`contracts/errors.yaml`、OpenAPI 总入口、CI 工作流、Compose 总入口、`kjx-server` 总装配、Flyway 版本段分配。

## 模块依赖（冻结）

业务模块只能依赖目标模块公开的 `api`/`port` 包。禁止跨模块访问 Mapper、Entity、Repository 实现和内部 Service。`kjx-runtime` 与 `kjx-tool` 都只依赖 `kjx-runtime-api`，不得相互依赖。`kjx-server` 不写领域业务。ClickHouse 客户端不得出现在核心领域模块。

## CI

`.github/workflows/ci.yml` 按目录执行：

- `contracts/` YAML/JSON 解析与错误码基线
- `backend/` Maven `test`
- `apps/web`、`apps/admin` 类型检查（workspace 根 `apps/` 或各包 lockfile + frozen install）

骨架阶段若目录或 lockfile 尚未就绪，对应 **job 会被 skip**（GitHub 显示 skipped），**不会** `exit 0` 冒充测试通过。lockfile 已存在但没有 `typecheck` / `tsconfig.json` 时会失败。

## 安全

- 复制 `.env.example` 为 `.env`，只保留假值或本机密钥。
- 禁止提交真实密钥、手机号明文、Sa-Token、证书。
- `kjx_account_device` 只存 `token_hash`，不存明文 token。
- 生产密钥来自环境 / KMS，轮换手机号 HMAC 密钥需重建索引。

# 发布 / 回滚手册（WP-51）

本手册描述叩君芯会员产品平台在**当前仓库已有部署形态**下的发布前检查、发布与回滚。  
仓库现状：`deploy/compose/docker-compose.dev.yml` 是唯一 Compose 栈（工程名 `kjx-dev`）。**没有**单独的生产 Compose，也没有独立的镜像仓库服务名。不要把 Higress、ClickHouse 或观测栈写进该文件。

本手册**未在 staging 执行过**。按步骤做完并把结果记入发布记录后，才算演练完成。

## 0. 当前可启动的组件

`deploy/compose/docker-compose.dev.yml` 中的服务（不要编造其它名字）：

| Compose 服务 | 容器名 | Profile | 作用 |
|---|---|---|---|
| `mysql` | `kjx-mysql` | 默认 | MySQL 8.4，库名来自 `${MYSQL_DATABASE:-koujunxin}`，数据卷 `kjx_mysql_data` |
| `redis` | `kjx-redis` | 默认 | Redis 8（AOF + 密码），数据卷 `kjx_redis_data` |
| `nginx` | `kjx-nginx` | `proxy` | 反代宿主机 `8080` / `3000` / `3001` |
| `backend` | `kjx-server` | `backend` | 在容器内对 **`kjx-server` 模块**执行 `mvn -B -ntp -pl kjx-server -am spring-boot:run` |
| `web` | `kjx-web` | `frontend` | 会员前台 |
| `admin` | `kjx-admin` | `frontend` | 管理端 |

后端入口永远是 **`backend/kjx-server`**（Spring Boot 组合根）。本机不走容器时：

```bash
docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml up -d
```

然后在 `backend/` 启动 `kjx-server`（与 Compose `backend` profile 的命令一致）：

```bash
mvn -B -ntp -pl kjx-server -am spring-boot:run
```

观测栈是另一份文件 `deploy/observability/docker-compose.yml`（工程名 `kjx-obs`），**不是**发布回滚的前置，也不是本手册的启动目标。

## 1. 压测 / 预发环境硬约束

发布候选与压测**禁止**打真实短信、真实支付。

- 短信：`kjx.sms.mode`（环境变量 `KJX_SMS_MODE`）必须是 `stub` 或 `fail-closed`，**禁止** `live`。开发 profile 缺省已是 `stub`（`application-dev.yml`）。不要配置生产 `SMS_ACCESS_KEY_ID` / `SMS_ACCESS_KEY_SECRET`。
- 支付：`kjx-billing` 不包含真实支付宝/微信 SDK。压测不得指向生产回调 URL，不得注入生产支付密钥。只用沙箱或本地 HMAC 验签。
- 压测脚本见 `tests/k6/`。脚本在 `BASE_URL` 主机名含 `prod` / `production` 时会直接退出。
- 不要对生产库跑 k6。不要对 `kjx_mysql_data` 做 `down -v`。

## 2. 发布前：Flyway 只向前

Flyway 是唯一 Schema 入口。规则：

1. **只追加新版本**，禁止改已经执行过的 `V*.sql` 内容。
2. **禁止**用 `baseline-on-migrate=true` 掩盖业务库状态错误（见 `deploy/README.md`）。`application-prod.yml` 已设 `spring.flyway.baseline-on-migrate: false`。
3. **禁止**手工改 `flyway_schema_history` 来“撤销”已执行脚本。
4. 版本段必须落在约定区间；重复版本号视为发布阻断。

发布前在仓库根目录执行（Windows PowerShell）：

```powershell
powershell -NoProfile -File tests/wp51/check-flyway-versions.ps1
```

退出码必须为 0。空库能否从零迁到当前头，以 `backend/kjx-server` 的 `FlywayBaselineIT` 为准（本机/CI 跑测试，不是本手册替你跑）。

核对目标库（只读）：

```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, success
  FROM flyway_schema_history
 ORDER BY installed_rank;
```

- `success` 必须全部为 `1`。
- 已记录脚本的 checksum 必须与即将发布的 JAR 内同名文件一致。若不一致：说明有人改了已执行脚本，**停止发布**，不要 `flyway repair` 后强行上线。
- 本次发布若包含新 `V*.sql`，它们必须是 history 中尚不存在的更大版本，而不是插入到中间或复用旧号。

## 3. 发布步骤

以下针对当前开发 Compose + `kjx-server`。将来若有独立镜像仓库，仍然遵守同一原则：**先扩应用、后缩应用；数据库只向前迁移**。

1. 确认 `.env` 已从 `.env.example` 复制且**不会提交**。密码、密钥只走环境变量。
2. 确认第 1 节压测约束与第 2 节 Flyway 检查已通过。
3. 记录当前运行版本：git 提交、`kjx-server` 构建时间、`flyway_schema_history` 最大版本、`GET /actuator/health` 结果。
4. 启动或更新中间件（不要带 `-v`）：

   ```bash
   docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml up -d
   ```

5. 需要统一入口时再开 Nginx：

   ```bash
   docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml --profile proxy up -d
   ```

6. 发布应用 = 用**新提交**启动 `kjx-server`（容器 `backend` profile 或宿主机 `mvn -pl kjx-server -am spring-boot:run`）。Flyway 在应用启动时只应执行**尚未记录**的新脚本。
7. 冒烟（经 Nginx 或直连 `:8080`）：
   - `GET /actuator/health`（`deploy/README.md` 路由：`/actuator/` → 后端 `:8080`）
   - 一条只读业务请求（例如已发布作品列表）
8. 观察 Outbox 是否开始消化（见 `deploy/runbooks/outbox-replay.md`）。积压告警看 `deploy/observability/prometheus/alerts.yml`，不要另写规则文件。

发布失败时进入第 4 节，**不要**删库重来。

## 4. 回滚：回退应用，而不是删库

回滚对象是 **`kjx-server` 正在运行的代码/制品**，以及可选的 `web` / `admin` / `nginx`。  
**不是**删除 `kjx_mysql_data`，不是 `DROP DATABASE`，不是清空 `flyway_schema_history`。

### 4.1 何时可以只回退应用

同时满足：

- 本次发布**没有**已在目标库执行成功的新 Flyway 脚本；或
- 新脚本对旧 `kjx-server` **向后兼容**（只加列/加表，旧代码不依赖新列且能在新 Schema 上启动）。

操作：

1. 停掉当前 `kjx-server`（Compose：`docker compose ... stop backend`；宿主机：结束对应 Java 进程）。**保持 `mysql` / `redis` 运行。**
2. 用发布前记录的 git 提交或旧制品重新启动 `kjx-server`：
   - 容器：把 `backend/` 工作副本回到旧提交后，再 `--profile backend up -d`（Compose 用的是挂载源码 + `maven:3.9-eclipse-temurin-21`，没有单独的应用镜像名）。
   - 宿主机：检出旧提交后再次 `mvn -B -ntp -pl kjx-server -am spring-boot:run`。
3. `GET /actuator/health` 恢复。
4. 只读核对 `flyway_schema_history`：行数不应因为回滚而减少。

前端回滚同理：停 `web` / `admin`（或宿主机 Next/Vite），切回旧提交再起。Nginx 配置回退改的是 `deploy/nginx/`，不是数据库。

### 4.2 何时不能只回退应用

若新迁移已经执行，且旧代码无法在新 Schema 上运行（删列、改约束、改语义）：

1. **不要**改已执行的 SQL 再启动一次指望 Flyway 倒车。
2. **不要**在正在服役的 `kjx-mysql` 上覆盖恢复。
3. 按 `deploy/runbooks/mysql-restore.md` 把备份恢复到**新实例**，核对 `flyway_schema_history` 后，再把 `kjx-server` 的数据源切到新实例。
4. 切流完成前，旧实例保持只读或停止写入，避免双写。

### 4.3 明确禁止

- `docker compose ... down -v`（会删 `kjx_mysql_data` / `kjx_redis_data`）
- 对业务库 `flyway baseline` / `repair` 当常规回滚手段
- 为了“干净”而 `DELETE FROM flyway_schema_history`
- 回滚时把 `KJX_SMS_MODE` 改成 `live` 或接生产支付

## 5. 发布记录（每次必填）

- 日期与操作人
- 发布前 git 提交 / 发布后 git 提交
- Flyway 最大版本（发布前 / 发布后）
- `check-flyway-versions.ps1` 退出码
- `GET /actuator/health` 结果
- 是否执行过回滚；若执行，回退到哪一次提交
- 是否动过数据库（只能是向前迁移；否则写明走了新实例恢复）

未填写以上记录，不得声称本次发布或回滚已经验收。

# koujunxin-backend

叩君芯模块化单体后端。唯一 Spring Boot 进程是 `kjx-server`。

## 要求

- JDK 21（Eclipse Temurin）
- Maven 3.9+
- 本目录为 Maven 聚合根，不要在仓库根目录执行 `mvn`

## 模块

| 模块 | 职责 |
|---|---|
| `ruoyi-common` | 从 RuoYi-Vue-Plus v5.2.3 可追溯裁剪的 common 能力 |
| `ruoyi-common-idempotent` | MySQL 事务内幂等守护 |
| `kjx-modules/*` | 自研业务 jar |
| `kjx-notification` | Transactional Outbox 写入、投递、重试与消费幂等 |
| `kjx-runtime-api` | Runtime/Tool 中立端口与 DTO，禁止依赖实现模块 |
| `kjx-server` | 唯一启动与装配模块，不写领域业务 |

`kjx-runtime` 与 `kjx-tool` 都只依赖 `kjx-runtime-api`，由 Maven Enforcer（循环依赖 + bannedDependencies）和 ArchUnit 强制。

## 本地启动

```bash
cd backend
mvn -q test
mvn -pl kjx-server -am spring-boot:run
```

默认端口 `8080`。开发启动前先在仓库根目录启动 MySQL 8.4 与 Redis 8：

```bash
docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml up -d
```

Flyway 是唯一建表入口，`baseline-on-migrate=false`。生产 baseline 不创建默认管理员、已知密码或客户端密钥；管理员账号必须通过后续安全引导流程创建。

测试包含 MySQL 8.4 / Redis 8 Testcontainers。Docker 可用时，`mvn test` 会执行 Flyway、双域 Token、部门隔离、Redisson、幂等和 Outbox 集成测试。

## 版本

所有依赖版本集中在本目录 `pom.xml` 的 `properties` / `dependencyManagement`。子模块只写 `groupId` + `artifactId`。

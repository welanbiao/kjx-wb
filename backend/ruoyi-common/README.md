# ruoyi-common 来源与裁剪

## 上游

| 项 | 值 |
|---|---|
| 仓库 | https://github.com/dromara/RuoYi-Vue-Plus |
| Tag | `v5.2.3` |
| Tag object | `1335ab57b53d9ce552026e0ef0f5aca043aa8732` |
| Commit | `015b4060011ed00262369d49fc36d420f2f040a4` |
| 提交说明 | `!591 发布 5.2.3 正式版 Merge pull request !591` |
| 日期 | 2024-10-25 |
| 许可证 | MIT |
| 上游栈 | Spring Boot 3.2.11 / Sa-Token 1.39.0 / Redisson 3.37.0 / MyBatis-Plus 3.5.8 |

本仓库后端基线是 Spring Boot 3.5.16、Sa-Token 1.46.0、Redisson 4.7.0、MyBatis-Plus 3.5.17。Java 源码来自上述 tag，POM 与少量适配面向本仓库基线。

## 保留模块

- `ruoyi-common-core`
- `ruoyi-common-json`
- `ruoyi-common-web`（去掉 Undertow，使用 Tomcat）
- `ruoyi-common-redis`（去掉 lock4j）
- `ruoyi-common-satoken`（去掉单一 JWT StpLogic，增加 app/admin）
- `ruoyi-common-mybatis`（去掉 dynamic-datasource / p6spy；保留 `@DataPermission`）
- `ruoyi-common-ratelimiter`
- `ruoyi-common-log`
- `ruoyi-common-excel`

`ruoyi-common-idempotent` 由 WP-02 所有，本包只在聚合 POM 中保留模块入口，不修改其内容。

## 明确未导入

整仓复制禁止。未导入：job、mail、oss、sms、social、sse、tenant、websocket、doc、encrypt、security、sensitive、translation、workflow、代码生成。

## KJX 适配（相对上游源码）

1. `groupId` = `com.koujunxin`，包名仍为 `org.dromara.*`。
2. `StpKit` + `SaTokenConfig`：loginType `app` / `admin`，默认 `StpUtil` 指向 admin。
3. 未登录 HTTP 401（Gate A）。
4. `RegionUtils`：无 `ip2region.xdb` 时降级，不阻断启动。
5. `RedisExceptionHandler`：不依赖 lock4j。
6. Flyway V1 来自 `script/sql/ry_vue_5.X.sql` 裁剪，字符集覆盖为 utf8mb4_0900_ai_ci。
7. `LoginUser.getLoginId()`：Sa-Token 1.46 禁止冒号，改为纯 userId；域隔离由 `StpKit` loginType 承担。
8. `PlusSaTokenDao` 实现 `SaTokenDaoBySessionFollowObject`（1.46 Session API）。
9. Redisson 4.7.0 与 5.2.3 Redis 层不兼容，本聚合覆盖为 3.45.1。
10. `DataBaseHelper` 改为单 `DataSource`，不引入 dynamic-datasource。

## 启动

```bash
cd backend
mvn -pl kjx-server -am test
# 中间件
# 仓库根目录：docker compose --env-file .env -f deploy/compose/docker-compose.dev.yml up -d
mvn -pl kjx-server -am spring-boot:run
```

时区 `Asia/Shanghai`。Flyway `baseline-on-migrate=false`。空库直接 migrate；已有非 Flyway 库会失败，这是预期。

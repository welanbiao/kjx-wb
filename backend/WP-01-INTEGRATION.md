# WP-01 集成请求（根 POM / 模块聚合）

WP-01 不能修改 `backend/pom.xml`。以下变更请集成 Agent 合并到聚合 POM 的 `properties` / `dependencyManagement`，之后子模块可去掉显式 `<version>`。

## 1. 必须：Redisson 回退到 3.45.1

根 POM 当前 `redisson.version=4.7.0`。4.7.0 **可以解析下载**，但与 RuoYi-Vue-Plus v5.2.3 Redis 层 **API 不兼容**（已实测编译失败）：

- `org.redisson.api.NameMapper` 不在 4.7.0 的 `org.redisson.api` 包
- `org.redisson.spring.cache.CacheConfig` / `RedissonCache` 包结构变更

`backend/ruoyi-common/pom.xml` 已覆盖为 **`3.45.1`**。锁 / 限流 / 延迟队列已在 `RedissonCompatibilityIT` 对 Redis 8 实测通过。

请把根 POM `<redisson.version>` 改为 `3.45.1`，并把 `redisson-spring-boot-starter` 放进 dependencyManagement。不要引入 lock4j。

## 2. dependencyManagement 提升

把 `backend/ruoyi-common/pom.xml` 的 dependencyManagement 提升到 `backend/pom.xml`：

```xml
<dependency>
  <groupId>cn.hutool</groupId>
  <artifactId>hutool-bom</artifactId>
  <version>${hutool.version}</version>
  <type>pom</type>
  <scope>import</scope>
</dependency>
<dependency>
  <groupId>com.baomidou</groupId>
  <artifactId>mybatis-plus-bom</artifactId>
  <version>${mybatis-plus.version}</version>
  <type>pom</type>
  <scope>import</scope>
</dependency>
<dependency>
  <groupId>cn.dev33</groupId>
  <artifactId>sa-token-spring-boot3-starter</artifactId>
  <version>${sa-token.version}</version>
</dependency>
<dependency>
  <groupId>cn.dev33</groupId>
  <artifactId>sa-token-core</artifactId>
  <version>${sa-token.version}</version>
</dependency>
<dependency>
  <groupId>org.redisson</groupId>
  <artifactId>redisson-spring-boot-starter</artifactId>
  <version>${redisson.version}</version>
</dependency>
```

本仓库模块（版本 `${project.version}`）：

- `com.koujunxin:ruoyi-common-core`
- `ruoyi-common-json`
- `ruoyi-common-web`
- `ruoyi-common-redis`
- `ruoyi-common-satoken`
- `ruoyi-common-mybatis`
- `ruoyi-common-ratelimiter`
- `ruoyi-common-log`
- `ruoyi-common-excel`
- `ruoyi-common-idempotent`（WP-02，仅聚合入口）

建议提升的 properties（现写在 `ruoyi-common/pom.xml`）：

| 属性 | 建议值 | 用途 |
|---|---|---|
| `redisson.version` | **3.45.1**（覆盖 4.7.0） | Redis / 锁 / 限流 / 延迟队列 |
| `ip2region.version` | 2.7.0 | 离线 IP（缺 xdb 时降级） |
| `mapstruct-plus.version` | 1.4.5 | `MapstructUtils` / `BaseMapperPlus` |
| `easyexcel.version` | 4.0.3 | ruoyi-common-excel |

`kjx-server/pom.xml` 里 `ruoyi-common-*` 目前写了 `${project.version}`，提升后请删掉这些 version。

Spring Boot parent 已管理、无需再写 version：`flyway-core`、`flyway-mysql`、`mysql-connector-j`、`testcontainers`、`caffeine`、`lombok`。

## 3. 不要改的

- 不要把 Higress / ClickHouse 加进 `deploy/compose`。
- 不要把 `ruoyi-common-idempotent` 换成上游 Redis 幂等实现，也不要改该模块源码。
- 不要设置 `spring.flyway.baseline-on-migrate=true`。
- 不要把 `ruoyi-common` 整仓复制或伪造框架源码。

## 4. Flyway 版本段

WP-01：

- `V1__baseline_ruoyi_vue_plus.sql`（RuoYi `script/sql/ry_vue_5.X.sql` 裁剪）
- `V1.1__seed_kjx_roles_and_depts.sql`

IT 时 classpath 还会扫到其他模块的 `V100+`（WP-02 幂等、通知 outbox）。空库会一起 migrate，这是预期。业务表仍按 docs/13 的 V200+ 段。

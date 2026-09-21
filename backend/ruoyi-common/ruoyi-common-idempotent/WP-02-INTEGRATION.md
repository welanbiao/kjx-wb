# WP-02 聚合 / 依赖管理请求（集成 Agent）

本工作包**不能**修改 `backend/pom.xml`、`backend/kjx-modules/pom.xml`、`backend/ruoyi-common/pom.xml`、`backend/kjx-server/**`。下列变更请由集成 Agent / WP-01 合入。子模块 POM **未散落版本号**，坐标版本一律走父 BOM / `${project.version}` / 已有 properties。

## 1. `backend/ruoyi-common/pom.xml`

在聚合中增加模块：

```xml
<modules>
  <module>ruoyi-common-idempotent</module>
</modules>
```

## 2. `backend/pom.xml` `dependencyManagement`

登记内部模块（与现有 kjx-* 条目同一风格）：

```xml
<dependency>
  <groupId>com.koujunxin</groupId>
  <artifactId>ruoyi-common-idempotent</artifactId>
  <version>${project.version}</version>
</dependency>
```

可选（当前子模块已用 Spring Boot 3.5 BOM，无需写 version；若要显式收敛可再登记）：

- `org.testcontainers:testcontainers` / `junit-jupiter` / `mysql`
- `org.flywaydb:flyway-core` / `flyway-mysql`
- `com.fasterxml.jackson.core:jackson-databind`（Boot BOM 已管理）
- 若后续改为 JSON Schema 库校验：`com.networknt:json-schema-validator`，version 用已有 `${json-schema-validator.version}`（请放进 dependencyManagement，避免子模块写 version）

## 3. `backend/kjx-server/pom.xml`

```xml
<dependency>
  <groupId>com.koujunxin</groupId>
  <artifactId>ruoyi-common-idempotent</artifactId>
</dependency>
```

`kjx-notification` 已在 server 依赖中。不要把幂等/Outbox 业务逻辑写进 server。

## 4. Flyway 扫描

WP-02 迁移在模块 classpath：

| 版本 | 模块 | 脚本 |
|---|---|---|
| V100 | `ruoyi-common-idempotent` | `db/migration/V100__create_kjx_idempotency_guard.sql` |
| V101 | `kjx-notification` | `db/migration/V101__create_kjx_outbox_event.sql` |
| V102 | `kjx-notification` | `db/migration/V102__create_kjx_outbox_consume_receipt.sql` |

`kjx-server` 建议：

```yaml
spring.flyway.locations: classpath:db/migration
```

标准 `classpath:` 会扫描依赖 JAR；Flyway 不接受 Spring 风格的 `classpath*:`。版本号不重叠，可与 WP-01 的 V1.x 段衔接。

**V102 说明**：`kjx_outbox_consume_receipt` **不是** docs/13 冻结表，未改 `kjx_outbox_event` 列。用于 `event_id + channel` 消费幂等。若集成策略要求该表先 ADR，可暂缓合入 V102，保留 `ConsumerIdempotencyPort` 接口。

## 5. 装配约定

- `IdempotencyService` / `OutboxWriter` 必须在**当前 Spring 事务**内调用。
- `OutboxWorker` 依赖业务侧提供的 `OutboxPublisher` bean；无 Publisher 时不创建 Worker。
- 时区 `Asia/Shanghai`（连接 `SET time_zone = '+08:00'`）。
- 不要同时启用 RuoYi Redis 幂等与本模块，避免双实现。
- `kjx-runtime` / `kjx-tool` 只依赖 `kjx-runtime-api`，不要依赖本幂等/Outbox 的 JDBC 实现细节以外的内部类；业务模块通过公开 API 调用即可。

## 6. 独立验证（聚合未合入前）

```text
mvn -f backend/kjx-modules/kjx-runtime-api/pom.xml test
mvn -f backend/ruoyi-common/ruoyi-common-idempotent/pom.xml test
mvn -f backend/kjx-modules/kjx-notification/pom.xml test
```

Testcontainers MySQL 8.4；`disabledWithoutDocker = true`，无 Docker 时跳过 IT，**禁止用 H2 替代**。

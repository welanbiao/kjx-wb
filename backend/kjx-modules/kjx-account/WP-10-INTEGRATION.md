# WP-10 集成请求（根 POM / kjx-server / 总 OpenAPI / errors.yaml）

本工作包**不能**修改根 POM、`kjx-server`、OpenAPI 总入口、`contracts/errors.yaml`。下列变更请集成 Agent 合入。

## 1. 已由本模块完成、装配即可用

- 模块：`com.koujunxin:kjx-account`（`kjx-server` 已依赖该坐标）。
- Flyway：`V200__create_kjx_account.sql`、`V201__create_kjx_account_device.sql`、`V202__create_kjx_verify_code.sql`。
- Spring Boot 自动配置：`AccountAutoConfiguration`（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）。
- 图形验证码：**端口** `CaptchaVerificationPort`，默认 fail-closed。
- 安全通知：只调 WP-02 `OutboxWriter`，模板 `ACCOUNT_REGISTERED` / `SECURITY_LOGIN` / `SECURITY_PASSWORD_CHANGED` / `ACCOUNT_CANCEL_SCHEDULED`。
- C 端会话：生产 `SaTokenAppSessionPort` 对齐 WP-01 `StpKit.APP`（loginType=app）。

## 2. `kjx-server` / Flyway 扫描

保持 Flyway 标准位置：

```yaml
spring.flyway.locations: classpath:db/migration
```

Flyway 会扫描依赖 JAR 中的同名 classpath 位置；不要使用 Flyway 不支持的 `classpath*:` 前缀。集成测试应断言 V200–V202 出现在 `flyway_schema_history`。

并注入密钥（不要写进仓库）：

```yaml
# 仅作键名说明，值必须来自环境
# PHONE_CRYPTO_KEY: <base64 32 bytes>
# PHONE_HMAC_SECRET: <base64 32 bytes>
kjx:
  account: {}   # 可选覆盖 ttl；默认 5 次失败锁 15 分钟、验证码 5 分钟、冷静期 7 天、token 2592000s
```

`PhoneCipher` 读取环境变量 / JVM 属性 `PHONE_CRYPTO_KEY`、`PHONE_HMAC_SECRET`，与根 `.env.example` 和 Compose 保持一致。缺失时进程无法创建 `PhoneCipher` bean。

建议同时设置 `spring.flyway` 能看到 WP-02 的 V100–V102，否则账号事务写 Outbox/幂等会缺表。

## 3. 必须提供的 Bean（kjx-server 或后续 WP）

```java
@Bean
CaptchaVerificationPort captchaVerificationPort(ImageCaptchaApplication app) {
    return (id, data) -> app.matching(id, data).isSuccess(); // tianai-captcha
}

@Bean
SmsDispatchPort smsDispatchPort(/* SMS4J */) {
    return (scene, phone, code) -> { /* 发送 6 位码；禁止 log phone/code */ };
}
```

- **不要**把验证码明文写入 Outbox（信封会拒绝 `verify_code` / 明文手机号）。
- WP-11 实现 `BoundMemberQueryPort` 后，登录响应 `member` 才非 null。
- 双域拦截已在 WP-01：`/app/auth/**` 匿名，`/app/account/**` 需 `StpKit.APP`。

## 4. 根 POM `dependencyManagement`

`org.bouncycastle:bcprov-jdk18on:1.80` 已提升到根 `dependencyManagement`，子模块不再散落版本。

可选登记：`cloud.tianai.captcha`（父 POM 已有 `tianai-captcha.version=1.5.2`，但未进 dependencyManagement）。

## 5. 总 OpenAPI

分片已更新：`contracts/openapi/paths/app-account.yaml`、`schemas/account.yaml`（本 WP 所有权）。

请在 `contracts/openapi/openapi.yaml` 增加冷静期撤销（docs/13 冻结清单未列此 path，属 WP-10 交付缺口）：

```yaml
  /app/account/cancel/revoke:
    $ref: "./paths/app-account.yaml#/revokeAccountCancel"
```

**不要改** `contracts/errors.yaml`。手机号已注册使用 HTTP 409 + body.code 409，无新 10xx 码。建议后续 ADR 若需要再补业务码。

账号成功响应 `code=0`（冻结信封），与 RuoYi `R.SUCCESS=200` 不同。本模块控制器返回 `ApiBody`，请勿被全局 `R` 包装覆盖。

## 6. 日志与安全

- 禁止日志输出手机号、验证码、`Authorization`。
- 库表无明文手机号 / 密码 / token；设备仅 `token_hash`。
- 密钥轮换见同目录 `KEY-ROTATION.md`。
- 前端 Mock：`frontend-mock/account-mock.json`。

## 7. 独立验证

```text
mvn -f backend/kjx-modules/kjx-account/pom.xml test
```

MySQL 8.4 + Redis 8 Testcontainers；`disabledWithoutDocker = true`。无 Docker 时跳过 IT，**禁止用 H2 替代**。

# kjx-member（WP-11）

主播扩展、会员录入、三要素绑定、人工审核与申诉。额度只走唯一 `MemberRecalcService`。权益/解锁属 WP-13（V330+），本模块不实现。

## 范围

- `V300`–`V303`：`kjx_host` / `kjx_member` / `kjx_member_binding` / `kjx_binding_appeal`
- C 端：`/app/member/**`
- HOST 自助：`/admin-api/kjx/self/**`
- 运营：`/admin-api/kjx/member|binding|appeal|host/**`（不含 entitlement / unlock-record / work）

## 规则

- `subscribedMonths`、`subscribedDays`、`expireAt` 分别必填，互不推导。
- `available_count = floor(subscribed_days / 30)`；用户 `claimMonths` 不参与额度。
- 会员编码 `{HOST_CODE}-{8位}`，排除 O/0/I/1/L，唯一键冲突重试。
- 三要素：抖音号 trim/去@/小写，编码大写去空格去连字符，月份精确相等；失败统一 1001。
- 失败 3 次锁定 30 分钟（1004）。命中后一律 `PENDING_REVIEW`。
- 审核通过：binding + member + account.bind_status + AUDIT + Outbox 同事务；禁止事务内发短信。
- HOST A 不能读写 B 的 member/binding；运营路径与 HOST 自助分离。
- 关键修改 `reason` 必填并写 AUDIT。

## Flyway

标准位置：`classpath:db/migration`。不要使用 `classpath*:`。

## 测试

MySQL 8.4 + Redis 8 Testcontainers，`disabledWithoutDocker = true`，禁止 H2。

```text
mvn -f backend/pom.xml -pl kjx-modules/kjx-member -am test
```

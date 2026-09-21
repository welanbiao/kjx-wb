# WP-30 集成说明

状态：模块内实现与测试已落地。未改 `openapi.yaml`、`errors.yaml`、事件白名单、`kjx-server` 或根 POM。

## 行为

- 通道优先级：作品 > 会员 > 平台。BYOK 缺少凭据或上游失败时不回退平台余额。
- SSRF：每次请求和每次重定向都重新解析 DNS，任一私网、环回、链路本地或元数据地址即拒绝。
- 余额只由 `kjx_ledger_entry` 派生。`kjx_wallet` 只有行锁锚点，没有余额列。
- 预冻结写入 `kjx_balance_hold`。结算写入账本与 `kjx_usage_event`，并在同一事务写 Outbox 和审计端口。
- 支付回调先验签，再按幂等键入账。重复回调返回原流水；同键异参返回 1090。
- 账本 UPDATE/DELETE 由触发器拒绝。
- 分析投影在事务提交后调用，异常被吞掉，不回滚账本。

## 待主 Agent 接线

1. 把 `contracts/openapi/paths/billing.yaml` 与 `schemas/billing.yaml` 挂到 `openapi.yaml`。
2. 将 `contracts/events/billing-events.json` 合并进 `event-whitelist.json`：`billing.frozen`、`billing.settled`、`billing.released`、`payment.paid`。
3. 在 `kjx-server` 提供 `BillingOutboxPort`，事务内委托 `OutboxWriter`。
4. 提供 `BillingAuditPort`，事务内委托 `BillingLogWritePort`。
5. `BillingProjection` 只能在提交后投影 ClickHouse，默认不做。
6. 错误码 `1201 INSUFFICIENT_BALANCE`、`1202 BYOK_FAILED` 需补进 `contracts/errors.yaml`。当前 `1090` 复用既有幂等冲突。
7. 真实支付宝/微信 SDK、密钥和生产回调 URL 不在本包。验签使用调用方注入的 `HmacPaymentVerifier`。

## 版本

Flyway：`V500` 凭据引用，`V510`–`V515` 钱包、冻结、账本、用量、订单、对账。位置是 `classpath:db/migration`。账本触发器要求 MySQL 打开 `log_bin_trust_function_creators`，开发 Compose 与服务端集成测试容器已加上该参数。

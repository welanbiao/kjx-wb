# ADR-002 追认计费错误码 1201 / 1202

- 状态：Accepted
- 日期：2026-09-22
- 工作包：WP-30
- 影响：`contracts/errors.yaml`、`JdbcBillingService`、`BillingException`

## 背景

`docs/13-统一架构DDL与接口契约基线.md` 未冻结 `INSUFFICIENT_BALANCE` 与 `BYOK_FAILED`。`JdbcBillingService` / `BillingException` 已使用这两个码与文案：

- `BillingException.insufficient()` → name `INSUFFICIENT_BALANCE`，code `1201`，message `余额不足`
- `BillingException.byokFailed()` → name `BYOK_FAILED`，code `1202`，message `上游调用失败，未扣平台余额`

契约必须追认现状，不得改数值、不得改文案。本 ADR 不改 docs/13 已冻结条目。

## 决策

在 `contracts/errors.yaml` 追加（不改现有条目）：

| name | code | httpStatus | message | retryable |
|---|---|---|---|---|
| `INSUFFICIENT_BALANCE` | 1201 | 402 | 余额不足 | false |
| `BYOK_FAILED` | 1202 | 502 | 上游调用失败，未扣平台余额 | true |

402 / 502 是 HTTP 映射；业务 `body.code` 仍是 1201 / 1202。

## 后果

- 计费模块可继续按现有异常构造返回，契约与实现一致。
- docs/13 冻结面保持不动；后续新增计费错误码仍须先 ADR 再写入 `errors.yaml`。

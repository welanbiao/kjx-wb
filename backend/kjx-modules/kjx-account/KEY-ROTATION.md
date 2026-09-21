# 手机号加密密钥轮换说明（WP-10）

密钥**只允许环境变量 / KMS 注入**，禁止写入 Git、镜像层或 `application.yml` 明文。

| 变量 | 算法 | 长度 | 用途 |
|---|---|---|---|
| `PHONE_CRYPTO_KEY` | AES-256-GCM | 32 字节，生产使用 Base64 | `kjx_account.phone` 密文 |
| `PHONE_HMAC_SECRET` | HMAC-SHA256 | ≥16 字节，建议 32 字节，生产使用 Base64 | `kjx_account.phone_hash` / 验证码哈希胡椒 |

生成示例（勿把输出提交到仓库）：

```bash
openssl rand -base64 32   # AES
openssl rand -base64 32   # HMAC
```

## 轮换步骤

1. 准备新 AES、新 HMAC。HMAC 轮换会改变所有 `phone_hash`，**必须重建唯一索引数据**。
2. 维护窗口：读出旧密文 → 用旧 AES 解密（仅内存）→ 新 AES 加密 → 新 HMAC 写 `phone_hash`。
3. 同一事务批量更新 `kjx_account.phone` / `phone_hash` 与 `kjx_verify_code.target_hash`（历史审计行若仍需按号检索）。
4. 切换进程环境变量后滚动重启。旧密钥保留只读直至对账完成。
5. 轮换期间禁止注册/改密以外的写路径并发，避免双密钥窗口写花。

AES 可单独轮换（密文重加密，HMAC 不变则唯一索引不用重建）。HMAC 轮换成本高，应低频。

`kjx_account_device.token_hash` 是 Sa-Token 的 SHA-256，与手机号密钥无关；下线设备只按 `token_hash` / `device` 撤销，不存明文 token。

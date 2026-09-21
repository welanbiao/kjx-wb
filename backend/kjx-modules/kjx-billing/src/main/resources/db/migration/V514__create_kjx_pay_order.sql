-- WP-30 / V514：支付订单。回调验签在应用层完成，重复入账靠幂等键。
CREATE TABLE `kjx_pay_order` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `member_id`          BIGINT UNSIGNED NOT NULL,
  `dept_id`            BIGINT UNSIGNED NOT NULL,
  `provider`           VARCHAR(16)     NOT NULL,
  `out_trade_no`       VARCHAR(64)     NOT NULL,
  `amount`             DECIMAL(18,6)   NOT NULL,
  `status`             VARCHAR(16)     NOT NULL,
  `idempotency_key`    VARCHAR(64)     NOT NULL,
  `payload_hash`       CHAR(64)        NOT NULL,
  `provider_trade_no`  VARCHAR(64)     DEFAULT NULL,
  `created_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_pay_order_trade` (`out_trade_no`),
  UNIQUE KEY `uk_kjx_pay_order_idem` (`idempotency_key`),
  KEY `idx_kjx_pay_order_member` (`member_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付订单';

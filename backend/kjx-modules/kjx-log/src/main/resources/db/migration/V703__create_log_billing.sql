-- WP-50 / V703：计费日志信封。账本事实表仍由计费工作包持有；本表只存与业务同事务的 BILLING 日志。
-- 不依赖 kjx-billing 表，避免跨领域耦合。
CREATE TABLE `log_billing` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `log_id`           CHAR(36)        NOT NULL,
  `ts`               DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `trace_id`         VARCHAR(64)     NOT NULL,
  `request_id`       VARCHAR(64)     NOT NULL,
  `member_id`        BIGINT UNSIGNED DEFAULT NULL,
  `biz_type`         VARCHAR(32)     NOT NULL,
  `direction`        VARCHAR(16)     DEFAULT NULL,
  `amount`           DECIMAL(18,6)   DEFAULT NULL,
  `idempotency_key`  VARCHAR(64)     DEFAULT NULL,
  `payload`          JSON            DEFAULT NULL,
  `schema_version`   VARCHAR(8)      NOT NULL DEFAULT '1.0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_log_billing_log_id` (`log_id`),
  KEY `idx_log_billing_member_ts` (`member_id`, `ts`),
  KEY `idx_log_billing_biz_ts` (`biz_type`, `ts`),
  KEY `idx_log_billing_idem` (`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计费日志信封';

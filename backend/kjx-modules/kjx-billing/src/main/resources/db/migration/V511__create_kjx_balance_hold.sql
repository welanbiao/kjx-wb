-- WP-30 / V511：预冻结。余额仍只从账本派生，本表只记录未结算占用。
CREATE TABLE `kjx_balance_hold` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `member_id`        BIGINT UNSIGNED NOT NULL,
  `dept_id`          BIGINT UNSIGNED NOT NULL,
  `amount`           DECIMAL(18,6)   NOT NULL,
  `status`           VARCHAR(16)     NOT NULL,
  `idempotency_key`  VARCHAR(64)     NOT NULL,
  `payload_hash`     CHAR(64)        NOT NULL,
  `reason`           VARCHAR(200)    NOT NULL,
  `work_id`          BIGINT UNSIGNED DEFAULT NULL,
  `created_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_balance_hold_idem` (`idempotency_key`),
  KEY `idx_kjx_balance_hold_member_status` (`member_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='余额预冻结';

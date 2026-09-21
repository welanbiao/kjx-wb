-- WP-02 / V100：幂等守护表。字段与主键严格对齐 docs/13 §3.4。
-- 禁止增加 account_id / ref_type / locked_at 等未冻结列。
CREATE TABLE `kjx_idempotency_guard` (
  `scene`           VARCHAR(32)     NOT NULL,
  `ref_id`          BIGINT UNSIGNED NOT NULL,
  `idempotency_key` VARCHAR(64)     NOT NULL,
  `actor_id`        BIGINT UNSIGNED DEFAULT NULL,
  `request_hash`    CHAR(64)        NOT NULL,
  `status`          VARCHAR(16)     NOT NULL DEFAULT 'PROCESSING',
  `result_code`     VARCHAR(32)     DEFAULT NULL,
  `result_body`     JSON            DEFAULT NULL,
  `expire_at`       DATETIME(3)     NOT NULL,
  `create_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`scene`, `ref_id`, `idempotency_key`),
  KEY `idx_idem_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- WP-30 / V513：用量权威记录。对账只读本表与账本，不读 ClickHouse。
CREATE TABLE `kjx_usage_event` (
  `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `member_id`               BIGINT UNSIGNED NOT NULL,
  `dept_id`                 BIGINT UNSIGNED NOT NULL,
  `work_id`                 BIGINT UNSIGNED DEFAULT NULL,
  `channel`                 VARCHAR(16)     NOT NULL,
  `model_name`              VARCHAR(64)     NOT NULL,
  `input_tokens`            INT UNSIGNED    NOT NULL,
  `output_tokens`           INT UNSIGNED    NOT NULL,
  `cost`                    DECIMAL(18,6)   NOT NULL,
  `ledger_idempotency_key`  VARCHAR(64)     NOT NULL,
  `created_at`              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_usage_event_ledger` (`ledger_idempotency_key`),
  KEY `idx_kjx_usage_event_member_created` (`member_id`, `created_at`),
  KEY `idx_kjx_usage_event_work` (`work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用量事件';

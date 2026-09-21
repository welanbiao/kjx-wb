-- WP-30 / V515：对账差异。零差异不写告警行。
CREATE TABLE `kjx_reconcile_record` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `scope_type`       VARCHAR(32)     NOT NULL,
  `scope_id`         VARCHAR(64)     NOT NULL,
  `dept_id`          BIGINT UNSIGNED NOT NULL,
  `expected_amount`  DECIMAL(18,6)   NOT NULL,
  `actual_amount`    DECIMAL(18,6)   NOT NULL,
  `drift`            DECIMAL(18,6)   NOT NULL,
  `status`           VARCHAR(16)     NOT NULL,
  `detail`           VARCHAR(500)    NOT NULL,
  `created_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_kjx_reconcile_scope` (`scope_type`, `scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账差异';

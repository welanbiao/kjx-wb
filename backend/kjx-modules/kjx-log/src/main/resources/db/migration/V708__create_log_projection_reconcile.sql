-- WP-50 / V708：ClickHouse 分析投影对账。允许最终一致；不对核心业务路径可见。
CREATE TABLE `log_projection_reconcile` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `window_start`      DATETIME(3)     NOT NULL,
  `window_end`        DATETIME(3)     NOT NULL,
  `category`          VARCHAR(32)     NOT NULL,
  `mysql_published`   BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `receipt_done`      BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `clickhouse_rows`   BIGINT UNSIGNED DEFAULT NULL,
  `status`            VARCHAR(16)     NOT NULL,
  `detail`            VARCHAR(500)    DEFAULT NULL,
  `create_time`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_log_reconcile_cat_time` (`category`, `window_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分析投影对账';

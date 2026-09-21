-- WP-50 / V702：安全日志。不分区，按归档策略迁移。
CREATE TABLE `log_sec` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `log_id`          CHAR(36)        NOT NULL,
  `ts`              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `trace_id`        VARCHAR(64)     NOT NULL,
  `request_id`      VARCHAR(64)     NOT NULL,
  `event_type`      VARCHAR(64)     NOT NULL,
  `member_id`       BIGINT UNSIGNED DEFAULT NULL,
  `ip`              VARBINARY(16)   DEFAULT NULL,
  `outcome`         VARCHAR(16)     NOT NULL,
  `reason`          VARCHAR(256)    DEFAULT NULL,
  `payload`         JSON            DEFAULT NULL,
  `schema_version`  VARCHAR(8)      NOT NULL DEFAULT '1.0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_log_sec_log_id` (`log_id`),
  KEY `idx_log_sec_member_ts` (`member_id`, `ts`),
  KEY `idx_log_sec_event_ts` (`event_type`, `ts`),
  KEY `idx_log_sec_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全日志';

-- WP-50 / V701：业务操作日志。权威事实在 MySQL，不分区。
CREATE TABLE `log_biz` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `log_id`          CHAR(36)        NOT NULL,
  `ts`              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `trace_id`        VARCHAR(64)     NOT NULL,
  `request_id`      VARCHAR(64)     NOT NULL,
  `actor_type`      VARCHAR(16)     NOT NULL,
  `member_id`       BIGINT UNSIGNED DEFAULT NULL,
  `work_id`         VARCHAR(64)     DEFAULT NULL,
  `action`          VARCHAR(64)     NOT NULL,
  `outcome`         VARCHAR(16)     NOT NULL,
  `reason`          VARCHAR(256)    DEFAULT NULL,
  `payload`         JSON            DEFAULT NULL,
  `schema_version`  VARCHAR(8)      NOT NULL DEFAULT '1.0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_log_biz_log_id` (`log_id`),
  KEY `idx_log_biz_member_ts` (`member_id`, `ts`),
  KEY `idx_log_biz_work_ts` (`work_id`, `ts`),
  KEY `idx_log_biz_action_ts` (`action`, `ts`),
  KEY `idx_log_biz_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务操作日志';

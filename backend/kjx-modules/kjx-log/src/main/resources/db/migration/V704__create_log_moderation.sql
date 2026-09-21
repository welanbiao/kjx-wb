-- WP-50 / V704：内容审核日志。不存原文，默认只留哈希与脱敏预览。
CREATE TABLE `log_moderation` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `log_id`          CHAR(36)        NOT NULL,
  `ts`              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `trace_id`        VARCHAR(64)     NOT NULL,
  `request_id`      VARCHAR(64)     NOT NULL,
  `direction`       VARCHAR(16)     NOT NULL,
  `text_hash`       CHAR(64)        NOT NULL,
  `text_preview`    VARCHAR(50)     DEFAULT NULL,
  `hit_type`        VARCHAR(32)     DEFAULT NULL,
  `hit_words`       VARCHAR(128)    DEFAULT NULL,
  `action`          VARCHAR(16)     NOT NULL,
  `reviewer`        VARCHAR(64)     DEFAULT NULL,
  `review_result`   VARCHAR(32)     DEFAULT NULL,
  `schema_version`  VARCHAR(8)      NOT NULL DEFAULT '1.0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_log_moderation_log_id` (`log_id`),
  KEY `idx_log_mod_hit_ts` (`hit_type`, `ts`),
  KEY `idx_log_mod_dir_ts` (`direction`, `ts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容审核日志';

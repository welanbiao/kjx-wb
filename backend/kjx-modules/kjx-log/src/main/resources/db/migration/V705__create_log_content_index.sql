-- WP-50 / V705：AI 内容日志索引（指向对象存储）。默认关闭内容记录。
CREATE TABLE `log_content_index` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `log_id`          CHAR(36)        NOT NULL,
  `ts`              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `trace_id`        VARCHAR(64)     NOT NULL,
  `request_id`      VARCHAR(64)     NOT NULL,
  `account_id`      BIGINT UNSIGNED DEFAULT NULL,
  `channel`         VARCHAR(16)     NOT NULL,
  `object_key`      VARCHAR(512)    NOT NULL,
  `expire_at`       DATETIME(3)     NOT NULL,
  `schema_version`  VARCHAR(8)      NOT NULL DEFAULT '1.0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_log_content_log_id` (`log_id`),
  KEY `idx_log_content_expire` (`expire_at`),
  KEY `idx_log_content_account` (`account_id`, `ts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容日志对象索引';

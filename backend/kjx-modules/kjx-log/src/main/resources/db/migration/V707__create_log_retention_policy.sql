-- WP-50 / V707：留存策略配置。对齐 docs/06 §8.1。
CREATE TABLE `log_retention_policy` (
  `category`     VARCHAR(32)     NOT NULL,
  `hot_days`     INT UNSIGNED    NOT NULL,
  `total_days`   INT UNSIGNED    NOT NULL,
  `action`       VARCHAR(32)     NOT NULL,
  `update_time`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='日志留存策略';

INSERT INTO `log_retention_policy` (`category`, `hot_days`, `total_days`, `action`) VALUES
  ('BIZ', 0, 0, 'KEEP'),
  ('BILLING', 0, 0, 'KEEP'),
  ('AUDIT', 1095, 1095, 'ARCHIVE_THEN_APPROVE'),
  ('SEC', 365, 365, 'ARCHIVE_THEN_DELETE'),
  ('MOD', 365, 365, 'ARCHIVE_THEN_DELETE'),
  ('ACCESS', 30, 180, 'TTL'),
  ('AI_CALL', 90, 180, 'TTL'),
  ('TOOL_CALL', 90, 180, 'TTL'),
  ('AI_CONTENT', 7, 30, 'DELETE'),
  ('APP', 7, 30, 'DELETE'),
  ('TRACE', 7, 14, 'DELETE'),
  ('ERROR', 30, 90, 'DELETE');

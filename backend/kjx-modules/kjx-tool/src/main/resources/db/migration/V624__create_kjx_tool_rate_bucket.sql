-- WP-41 / V624：频控桶。session 累计、自然日累计、冷却时间共用 last_called_at。
CREATE TABLE `kjx_tool_rate_bucket` (
  `session_id`      VARCHAR(64)     NOT NULL,
  `tool_code`       VARCHAR(64)     NOT NULL,
  `bucket`          VARCHAR(48)     NOT NULL,
  `count`           INT UNSIGNED    NOT NULL DEFAULT 0,
  `last_called_at`  DATETIME(3)     NOT NULL,
  PRIMARY KEY (`session_id`, `tool_code`, `bucket`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工具频控';

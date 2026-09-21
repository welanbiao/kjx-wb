-- WP-41 / V623：工具调用幂等与结果快照。同一 session+idempotency_key 短路。
CREATE TABLE `kjx_tool_call` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `account_id`       BIGINT UNSIGNED NOT NULL,
  `work_code`        VARCHAR(64)     NOT NULL,
  `session_id`       VARCHAR(64)     NOT NULL,
  `tool_code`        VARCHAR(64)     NOT NULL,
  `tool_version`     VARCHAR(32)     NOT NULL,
  `idempotency_key`  VARCHAR(128)    NOT NULL,
  `arguments_hash`   CHAR(64)        NOT NULL,
  `status`           VARCHAR(16)     NOT NULL COMMENT 'PENDING/SUCCESS/FAILED',
  `result_code`      VARCHAR(64)     DEFAULT NULL,
  `result_message`   VARCHAR(512)    DEFAULT NULL,
  `result_payload`   JSON            DEFAULT NULL,
  `started_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at`      DATETIME(3)     DEFAULT NULL,
  `duration_ms`      INT             DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_tool_call_idem` (`session_id`, `idempotency_key`),
  KEY `idx_kjx_tool_call_account` (`account_id`, `started_at`),
  KEY `idx_kjx_tool_call_tool` (`tool_code`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工具调用幂等';

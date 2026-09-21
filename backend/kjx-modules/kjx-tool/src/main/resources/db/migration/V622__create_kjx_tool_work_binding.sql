-- WP-41 / V622：作品启用工具。custom 必须绑定；builtin 无绑定则按平台启停。
CREATE TABLE `kjx_tool_work_binding` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `work_code`     VARCHAR(64)     NOT NULL,
  `tool_code`     VARCHAR(64)     NOT NULL,
  `tool_version`  VARCHAR(32)     DEFAULT NULL,
  `enabled`       TINYINT         NOT NULL DEFAULT 1,
  `config`        JSON            DEFAULT NULL,
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_tool_work` (`work_code`, `tool_code`),
  KEY `idx_kjx_tool_work_code` (`tool_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作品工具启用';

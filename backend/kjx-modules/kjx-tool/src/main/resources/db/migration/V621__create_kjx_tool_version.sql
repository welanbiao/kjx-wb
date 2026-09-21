-- WP-41 / V621：工具版本。implementation.kind 仅 builtin/compose，禁止 script。
CREATE TABLE `kjx_tool_version` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tool_id`             BIGINT UNSIGNED NOT NULL,
  `version`             VARCHAR(32)     NOT NULL,
  `description`         VARCHAR(512)    NOT NULL DEFAULT '',
  `parameters_schema`   JSON            NOT NULL,
  `preconditions`       JSON            DEFAULT NULL,
  `implementation`      JSON            NOT NULL,
  `permissions`         JSON            DEFAULT NULL,
  `rate_limit`          JSON            DEFAULT NULL,
  `side_effects`        JSON            DEFAULT NULL,
  `audit`               TINYINT         NOT NULL DEFAULT 1,
  `timeout_ms`          INT UNSIGNED    NOT NULL DEFAULT 5000,
  `retry_max`           TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `status`              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'DRAFT/ACTIVE/DEPRECATED',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_tool_version` (`tool_id`, `version`),
  KEY `idx_kjx_tool_version_status` (`status`),
  CONSTRAINT `fk_kjx_tool_version_tool` FOREIGN KEY (`tool_id`) REFERENCES `kjx_tool` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工具版本与 Schema';

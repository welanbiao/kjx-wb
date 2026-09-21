-- WP-41 / V620：工具注册表。code 全局唯一，type 仅 builtin/custom。
CREATE TABLE `kjx_tool` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`          VARCHAR(64)     NOT NULL,
  `type`          VARCHAR(16)     NOT NULL COMMENT 'builtin/custom',
  `display_name`  VARCHAR(128)    NOT NULL,
  `enabled`       TINYINT         NOT NULL DEFAULT 1,
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_tool_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工具注册表';

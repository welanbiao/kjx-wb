-- WP-10 / V201：登录设备。只存 token_hash，禁止明文 Sa-Token（docs/13 §3.6）。
CREATE TABLE `kjx_account_device` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `account_id`     BIGINT UNSIGNED NOT NULL COMMENT '账号ID',
  `device_id`      VARCHAR(64)     NOT NULL COMMENT '设备指纹',
  `device_name`    VARCHAR(128)    DEFAULT NULL COMMENT '设备名（如 Chrome on Windows）',
  `platform`       VARCHAR(32)     DEFAULT NULL COMMENT 'web/ios/android',
  `last_ip`        VARBINARY(16)   DEFAULT NULL COMMENT '最后活跃 IP',
  `last_region`    VARCHAR(64)     DEFAULT NULL COMMENT '归属地（省市）',
  `last_active_at` DATETIME(3)     DEFAULT NULL COMMENT '最后活跃时间',
  `token_hash`     CHAR(64)        DEFAULT NULL COMMENT 'Sa-Token 哈希/会话ID（禁止存明文 token）',
  `create_time`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_device_account_device` (`account_id`, `device_id`),
  KEY `idx_kjx_device_account` (`account_id`, `last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号登录设备';

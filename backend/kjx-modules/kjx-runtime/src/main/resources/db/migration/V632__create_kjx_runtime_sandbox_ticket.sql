-- WP-42 / V632：沙箱 nonce 票据。iframe 只持有 nonce，不持有登录 token。
CREATE TABLE `kjx_runtime_sandbox_ticket` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `ticket_id`    VARCHAR(64)     NOT NULL COMMENT '票据ID',
  `nonce_hash`   CHAR(64)        NOT NULL COMMENT 'nonce SHA-256',
  `session_id`   VARCHAR(64)     NOT NULL COMMENT '绑定会话',
  `account_id`   BIGINT UNSIGNED NOT NULL COMMENT '绑定账号',
  `work_code`    VARCHAR(64)     NOT NULL COMMENT '绑定作品',
  `origin`       VARCHAR(255)    NOT NULL COMMENT '允许的 iframe origin，含 null',
  `expires_at`   DATETIME(3)     NOT NULL COMMENT '过期时间',
  `created_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_runtime_sandbox_ticket` (`ticket_id`),
  UNIQUE KEY `uk_kjx_runtime_sandbox_nonce` (`nonce_hash`),
  KEY `idx_kjx_runtime_sandbox_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙箱 nonce 票据';

-- WP-40 / V601：会话记忆锚点。不保存 runtime 剧情状态。
CREATE TABLE `kjx_ai_session` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`       VARCHAR(64)     NOT NULL COMMENT '与 runtime SessionRef.sessionId 对齐',
  `account_id`       BIGINT UNSIGNED NOT NULL COMMENT '账号',
  `work_code`        VARCHAR(64)     NOT NULL COMMENT '作品编码',
  `prompt_version`   VARCHAR(32)     DEFAULT NULL COMMENT '最近使用的 WORK Prompt 版本',
  `title`            VARCHAR(128)    DEFAULT NULL COMMENT '会话标题',
  `status`           VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CLOSED',
  `message_count`    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '消息条数',
  `last_turn_id`     VARCHAR(64)     DEFAULT NULL COMMENT '最近一轮 turn_id，供断线续传',
  `create_time`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_ai_session_id` (`session_id`),
  KEY `idx_kjx_ai_session_account` (`account_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 会话';

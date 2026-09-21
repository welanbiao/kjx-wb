-- WP-40 / V602：会话消息。不落模型 Key，不落 METHOD 母版。
CREATE TABLE `kjx_ai_message` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`       VARCHAR(64)     NOT NULL COMMENT '会话 ID',
  `seq`              INT UNSIGNED    NOT NULL COMMENT '会话内序号，从 1 递增',
  `turn_id`          VARCHAR(64)     NOT NULL COMMENT '轮次 ID',
  `role`             VARCHAR(16)     NOT NULL COMMENT 'user/assistant/tool',
  `content`          MEDIUMTEXT      DEFAULT NULL COMMENT '正文；system 不落客户端传入的母版',
  `tool_call_id`     VARCHAR(64)     DEFAULT NULL COMMENT 'tool 角色对应的 call id',
  `tool_calls`       JSON            DEFAULT NULL COMMENT 'assistant 的 tool_calls',
  `token_count`      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '估算 token',
  `create_time`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_ai_message_seq` (`session_id`, `seq`),
  KEY `idx_kjx_ai_message_turn` (`session_id`, `turn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 消息';

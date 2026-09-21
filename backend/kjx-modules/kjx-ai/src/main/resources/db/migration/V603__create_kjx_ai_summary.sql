-- WP-40 / V603：滚动摘要。覆盖 until_seq 之前的消息。
CREATE TABLE `kjx_ai_summary` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`       VARCHAR(64)     NOT NULL COMMENT '会话 ID',
  `until_seq`        INT UNSIGNED    NOT NULL COMMENT '已摘要到的消息序号（含）',
  `summary_text`     MEDIUMTEXT      NOT NULL COMMENT '摘要正文',
  `model`            VARCHAR(64)     DEFAULT NULL COMMENT '生成摘要的模型档，可空（本地压缩）',
  `create_time`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_kjx_ai_summary_session` (`session_id`, `until_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 会话摘要';

-- WP-42 / V630：运行时会话。状态由服务端权威持有，跨作品/账号用列约束拒绝。
CREATE TABLE `kjx_runtime_session` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`       VARCHAR(64)     NOT NULL COMMENT '对外会话ID',
  `account_id`       BIGINT UNSIGNED NOT NULL COMMENT '所有者账号',
  `work_code`        VARCHAR(64)     NOT NULL COMMENT '作品编码，会话内固定',
  `work_id`          BIGINT UNSIGNED DEFAULT NULL COMMENT '作品主键快照',
  `form`             VARCHAR(8)      NOT NULL DEFAULT 'A' COMMENT '接入形态 A/B/C',
  `stage`            VARCHAR(64)     NOT NULL DEFAULT '1' COMMENT '当前阶段',
  `schema_version`   VARCHAR(32)     NOT NULL COMMENT '状态 Schema 版本',
  `content_version`  VARCHAR(32)     NOT NULL COMMENT '会话内固定的内容版本',
  `payload_json`     JSON            NOT NULL COMMENT '中立状态 JSON',
  `checksum`         CHAR(64)        NOT NULL COMMENT 'payload 规范哈希',
  `status`           VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ENDED',
  `version`          INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `created_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_runtime_session_id` (`session_id`),
  KEY `idx_kjx_runtime_session_account_work` (`account_id`, `work_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行时会话';

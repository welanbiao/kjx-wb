-- WP-42 / V631：存档槽。写入必须带 schema/version/checksum/signature。
CREATE TABLE `kjx_runtime_save` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`       VARCHAR(64)     NOT NULL COMMENT '所属会话',
  `account_id`       BIGINT UNSIGNED NOT NULL COMMENT '所有者账号',
  `work_code`        VARCHAR(64)     NOT NULL COMMENT '作品编码',
  `slot`             VARCHAR(32)     NOT NULL COMMENT 'auto 或手动槽位',
  `schema_version`   VARCHAR(32)     NOT NULL COMMENT '存档 Schema 版本',
  `content_version`  VARCHAR(32)     NOT NULL COMMENT '内容版本',
  `stage`            VARCHAR(64)     NOT NULL COMMENT '存档时阶段',
  `state_json`       JSON            NOT NULL COMMENT '存档快照',
  `checksum`         CHAR(64)        NOT NULL COMMENT '规范哈希',
  `signature`        CHAR(64)        NOT NULL COMMENT 'HMAC 防篡改签名',
  `version`          INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '乐观锁，冲突即 SAVE_CONFLICT',
  `created_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_runtime_save_slot` (`session_id`, `slot`),
  KEY `idx_kjx_runtime_save_account_work` (`account_id`, `work_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行时存档';

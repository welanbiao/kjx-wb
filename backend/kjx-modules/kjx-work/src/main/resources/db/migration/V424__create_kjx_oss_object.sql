-- WP-31 / V424：对象存储元数据。真实云密钥不入库；签名密钥不写本表。
CREATE TABLE `kjx_oss_object` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scope`         VARCHAR(16)     NOT NULL COMMENT 'CDN/SERVER',
  `object_key`    VARCHAR(512)    NOT NULL COMMENT '对象键',
  `size_bytes`    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '字节数',
  `sha256`        CHAR(64)        NOT NULL COMMENT '内容哈希',
  `content_type`  VARCHAR(128)    DEFAULT NULL COMMENT 'MIME',
  `work_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '作品ID',
  `version`       VARCHAR(32)     DEFAULT NULL COMMENT '作品版本',
  `create_time`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_oss_object` (`scope`, `object_key`(191)),
  KEY `idx_kjx_oss_object_work` (`work_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OSS 对象元数据';

-- WP-31 / V421：素材元数据。CDN 与 server/ 用 scope 隔离。
CREATE TABLE `kjx_asset` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `work_id`       BIGINT UNSIGNED NOT NULL COMMENT '作品ID',
  `version`       VARCHAR(32)     NOT NULL COMMENT '作品版本',
  `asset_key`     VARCHAR(128)    NOT NULL COMMENT '素材业务键',
  `path`          VARCHAR(512)    NOT NULL COMMENT '包内相对路径',
  `content_type`  VARCHAR(128)    DEFAULT NULL COMMENT 'MIME',
  `size_bytes`    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '字节数',
  `sha256`        CHAR(64)        NOT NULL COMMENT '内容哈希',
  `width`         INT             DEFAULT NULL COMMENT '像素宽',
  `height`        INT             DEFAULT NULL COMMENT '像素高',
  `watermark_id`  VARCHAR(64)     DEFAULT NULL COMMENT '水印模板ID',
  `storage_key`   VARCHAR(512)    NOT NULL COMMENT '对象键',
  `scope`         VARCHAR(16)     NOT NULL DEFAULT 'CDN' COMMENT 'CDN/SERVER',
  `tags`          VARCHAR(255)    DEFAULT NULL COMMENT '标签逗号分隔',
  `license`       VARCHAR(255)    DEFAULT NULL COMMENT '版权来源',
  `creator`       VARCHAR(64)     DEFAULT '' COMMENT '创建者',
  `create_time`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_asset_path` (`work_id`, `version`, `path`),
  UNIQUE KEY `uk_kjx_asset_key` (`work_id`, `version`, `asset_key`),
  KEY `idx_kjx_asset_scope` (`scope`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='素材';

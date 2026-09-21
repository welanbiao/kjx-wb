-- WP-12 / V401：作品代码/包版本，与内容版本分离。
CREATE TABLE `kjx_work_version` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `work_id`        BIGINT UNSIGNED NOT NULL COMMENT '作品ID',
  `version`        VARCHAR(32)     NOT NULL COMMENT '语义化版本',
  `status`         VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_REVIEW/PUBLISHED/ROLLED_BACK',
  `form`           VARCHAR(8)      NOT NULL DEFAULT 'A' COMMENT '接入形态',
  `runtime_compat` VARCHAR(64)     DEFAULT NULL COMMENT '宿主兼容区间',
  `entry`          VARCHAR(255)    DEFAULT NULL COMMENT '入口（Form B/C）',
  `package_url`    VARCHAR(512)    DEFAULT NULL COMMENT '作品包存储地址',
  `package_size`   BIGINT UNSIGNED DEFAULT NULL COMMENT '作品包大小（字节）',
  `package_hash`   CHAR(64)        DEFAULT NULL COMMENT '作品包哈希',
  `validate_report` JSON           DEFAULT NULL COMMENT '机审报告',
  `gray_ratio`     INT             NOT NULL DEFAULT 0 COMMENT '灰度比例',
  `publish_time`   DATETIME(3)     DEFAULT NULL COMMENT '发布时间',
  `creator`        VARCHAR(64)     DEFAULT '' COMMENT '创建者',
  `create_time`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater`        VARCHAR(64)     DEFAULT '' COMMENT '更新者',
  `update_time`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_work_version` (`work_id`, `version`),
  KEY `idx_kjx_work_version_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作品版本';

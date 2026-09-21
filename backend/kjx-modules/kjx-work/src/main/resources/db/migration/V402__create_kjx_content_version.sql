-- WP-12 / V402：内容版本（与代码版本解耦：改台词不需重发前端）。
CREATE TABLE `kjx_content_version` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `work_id`     BIGINT UNSIGNED NOT NULL COMMENT '作品ID',
  `version`     VARCHAR(32)     NOT NULL COMMENT '内容版本号',
  `status`      VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ROLLED_BACK',
  `node_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '节点数',
  `content_url` VARCHAR(512)    DEFAULT NULL COMMENT '内容包存储地址',
  `content`     JSON            DEFAULT NULL COMMENT '内容数据（小体量时直接入库）',
  `review_time` DATETIME(3)     DEFAULT NULL COMMENT '内容审核时间',
  `publish_time` DATETIME(3)    DEFAULT NULL COMMENT '发布时间',
  `creator`     VARCHAR(64)     DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater`     VARCHAR(64)     DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_content_version` (`work_id`, `version`),
  KEY `idx_kjx_content_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容版本';

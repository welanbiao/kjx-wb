-- WP-31 / V423：从作品包 content/ 导入的内容文档（与 kjx_content_version 指针解耦）。
CREATE TABLE `kjx_content_document` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `work_id`     BIGINT UNSIGNED NOT NULL COMMENT '作品ID',
  `version`     VARCHAR(32)     NOT NULL COMMENT '作品版本',
  `doc_type`    VARCHAR(32)     NOT NULL COMMENT 'nodes/characters/fragments/metrics/endings',
  `content`     JSON            NOT NULL COMMENT '内容 JSON',
  `node_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '节点/条目数',
  `creator`     VARCHAR(64)     DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_content_document` (`work_id`, `version`, `doc_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容文档';

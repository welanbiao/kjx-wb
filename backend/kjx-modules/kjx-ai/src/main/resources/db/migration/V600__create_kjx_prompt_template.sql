-- WP-40 / V600：Prompt 版本。METHOD 母版永不下发；WORK 作为运行时稳定前缀来源。
CREATE TABLE `kjx_prompt_template` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `work_code`        VARCHAR(64)     NOT NULL COMMENT '作品编码；METHOD 母版可用 platform',
  `kind`             VARCHAR(16)     NOT NULL COMMENT 'METHOD/WORK',
  `prompt_mode`      VARCHAR(16)     NOT NULL DEFAULT 'SERVER' COMMENT 'SERVER/PACKAGE/BOTH',
  `version`          VARCHAR(32)     NOT NULL COMMENT '语义化版本',
  `status`           VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/ARCHIVED',
  `stable_prefix`    MEDIUMTEXT      NOT NULL COMMENT '人设/规则/工具定义，尽量不变以命中缓存',
  `tool_spec`        JSON            DEFAULT NULL COMMENT '工具说明快照',
  `variable_schema`  JSON            DEFAULT NULL COMMENT '可变后缀变量声明',
  `notes`            VARCHAR(255)    DEFAULT NULL COMMENT '备注，禁止写入密钥',
  `create_time`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_prompt_work_kind_ver` (`work_code`, `kind`, `version`),
  KEY `idx_kjx_prompt_active` (`work_code`, `kind`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Prompt 模板与版本';

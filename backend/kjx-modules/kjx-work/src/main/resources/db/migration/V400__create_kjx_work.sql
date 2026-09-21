-- WP-12 / V400：作品主表。字段与索引对齐 docs/09 §2.5 / 冻结 DDL。
CREATE TABLE `kjx_work` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `work_code`         VARCHAR(64)     NOT NULL COMMENT '作品ID（业务主键，如 breakup-editor）',
  `name`              VARCHAR(128)    NOT NULL COMMENT '作品名称',
  `work_type`         VARCHAR(16)     NOT NULL COMMENT 'layout（多轮对话式UI布局）/ game（AI文游）',
  `summary`           VARCHAR(500)    DEFAULT NULL COMMENT '简介',
  `cover_url`         VARCHAR(512)    DEFAULT NULL COMMENT '封面图 URL',
  `screenshots`       JSON            DEFAULT NULL COMMENT '截图 URL 数组',
  `category`          VARCHAR(32)     DEFAULT NULL COMMENT '分类',
  `tags`              VARCHAR(255)    DEFAULT NULL COMMENT '标签（逗号分隔）',

  `status`            VARCHAR(24)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_REVIEW/PUBLISHED/GRAY/UNPUBLISHED/REJECTED',
  `current_version`   VARCHAR(32)     DEFAULT NULL COMMENT '当前生效的作品版本',
  `content_version`   VARCHAR(32)     DEFAULT NULL COMMENT '当前生效的内容版本',

  `access_form`       VARCHAR(8)      NOT NULL DEFAULT 'A' COMMENT '接入形态 A/B/C',
  `prompt_mode`       VARCHAR(16)     NOT NULL DEFAULT 'SERVER' COMMENT 'SERVER/PACKAGE/BOTH',
  `allowed_channels`  VARCHAR(64)     NOT NULL DEFAULT 'PLATFORM,BYOK' COMMENT '允许的通道',
  `allow_byok_direct` BIT(1)          NOT NULL DEFAULT b'0' COMMENT '是否允许 BYOK 纯前端直连',
  `apps`              JSON            DEFAULT NULL COMMENT '启用的 App 容器（game 类）',
  `tools`             JSON            DEFAULT NULL COMMENT '启用的工具清单',
  `server_tools`      JSON            DEFAULT NULL COMMENT '服务端 Tool 清单（秘密出口）',

  `pricing_mode`      VARCHAR(16)     DEFAULT NULL COMMENT 'per_token/per_turn/per_call',
  `gray_ratio`        INT             NOT NULL DEFAULT 0 COMMENT '灰度比例 0-100',
  `sort_weight`       INT             NOT NULL DEFAULT 0 COMMENT '排序权重',
  `unlock_count`      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '解锁数（冗余统计）',
  `publish_time`      DATETIME(3)     DEFAULT NULL COMMENT '上架时间',

  `creator`           VARCHAR(64)     DEFAULT '' COMMENT '创建者',
  `create_time`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater`           VARCHAR(64)     DEFAULT '' COMMENT '更新者',
  `update_time`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`           BIT(1)          NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`         BIGINT          NOT NULL DEFAULT 0 COMMENT '租户编号（固定 0）',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_work_code` (`work_code`),
  KEY `idx_kjx_work_status` (`status`, `sort_weight`),
  KEY `idx_kjx_work_type` (`work_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作品';

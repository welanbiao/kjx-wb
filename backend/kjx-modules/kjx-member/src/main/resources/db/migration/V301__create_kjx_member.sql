-- WP-11 / V301：会员授权表。subscribed_months / subscribed_days / expire_at 独立必填，互不换算。
CREATE TABLE `kjx_member` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `member_code`       VARCHAR(64)     NOT NULL COMMENT '★会员编码（系统生成，格式 {主播短码}-{8位随机}，不接受人工输入）',
  `douyin_id`         VARCHAR(128)    NOT NULL COMMENT '抖音号（规范化后）',
  `douyin_id_raw`     VARCHAR(128)    DEFAULT NULL COMMENT '抖音号原始输入（审计用）',
  `dept_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '★主播部门（= sys_dept.id）；数据权限靠它自动隔离',

  `subscribed_months` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '已订阅月数（★绑定三要素比对字段）',
  `subscribed_days`   INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '已订阅天数（用于计算可解锁数）',

  `expire_at`         DATETIME(3)     NOT NULL COMMENT '到期时间',
  `start_at`          DATETIME(3)     DEFAULT NULL COMMENT '开通时间 = 到期时间 - 已订阅天数',
  `available_count`   INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '可解锁作品数 = floor(subscribed_days/30)（★M-1 修订）',
  `unlocked_count`    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '已解锁数量（物化，单一 recalc 维护）',
  `remain_count`      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '剩余可解锁数 = available_count - unlocked_count',
  `status`            VARCHAR(16)     NOT NULL DEFAULT 'NOT_STARTED' COMMENT 'VALID/EXPIRED/NOT_STARTED',

  `bind_state`        TINYINT         NOT NULL DEFAULT 0 COMMENT '0未绑定 1已绑定',
  `bound_account_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '已绑定账号（冗余，便于查询）',
  `bound_at`          DATETIME(3)     DEFAULT NULL COMMENT '绑定时间',

  `remark`            VARCHAR(255)    DEFAULT NULL COMMENT '备注',
  `version`           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator`           VARCHAR(64)     DEFAULT '' COMMENT '创建者',
  `create_time`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater`           VARCHAR(64)     DEFAULT '' COMMENT '更新者',
  `update_time`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`           BIT(1)          NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`         BIGINT          NOT NULL DEFAULT 0 COMMENT '租户编号（固定 0）',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_member_code` (`member_code`),
  UNIQUE KEY `uk_kjx_member_dept_douyin` (`dept_id`, `douyin_id`),
  KEY `idx_kjx_member_status` (`status`),
  KEY `idx_kjx_member_expire` (`expire_at`),
  KEY `idx_kjx_member_bind_state` (`bind_state`),
  KEY `idx_kjx_member_bound_account` (`bound_account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员授权表（权威数据源）';

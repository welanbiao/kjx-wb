-- WP-13 / V330：权益事实源。字段对齐 docs/09 §2.6；访问不得从计数字段推断。
-- uk_kjx_entitlement_member_work 保证每个 member/work 只有一条成功事实。
-- uk_kjx_entitlement_account_work 保证每个 account/work 只有一条成功事实（account_id 为空时 MySQL 允许多个 NULL）。
CREATE TABLE `kjx_member_entitlement` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dept_id`          BIGINT UNSIGNED NOT NULL COMMENT '★主播部门快照；数据权限直接作用于本表',
  `member_id`        BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
  `account_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '账号ID（冗余，便于按账号查"我的解锁"）',
  `work_id`          BIGINT UNSIGNED NOT NULL COMMENT '作品ID',
  `work_version`     VARCHAR(32)     DEFAULT NULL COMMENT '解锁时的作品版本',
  `source`           VARCHAR(16)     NOT NULL DEFAULT 'user' COMMENT 'user / official_add',
  `unlock_record_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联解锁记录',
  `unlocked_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '解锁时间',
  `create_time`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_entitlement_member_work` (`member_id`, `work_id`) COMMENT '★并发兜底（M-3）',
  UNIQUE KEY `uk_kjx_entitlement_account_work` (`account_id`, `work_id`) COMMENT '★每账号每作品一条成功事实',
  KEY `idx_kjx_entitlement_dept_member` (`dept_id`, `member_id`, `work_id`),
  KEY `idx_kjx_entitlement_account` (`account_id`),
  KEY `idx_kjx_entitlement_work` (`work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员作品权益';

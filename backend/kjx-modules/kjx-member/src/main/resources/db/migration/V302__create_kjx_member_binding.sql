-- WP-11 / V302：账号 ↔ 会员绑定。dept_id 从 member 快照写入，之后不随部门变更改历史。
CREATE TABLE `kjx_member_binding` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dept_id`          BIGINT UNSIGNED NOT NULL COMMENT '★主播部门快照；数据权限直接作用于本表',
  `account_id`       BIGINT UNSIGNED NOT NULL COMMENT '账号ID',
  `member_id`        BIGINT UNSIGNED NOT NULL COMMENT '会员ID',

  `douyin_id_input`  VARCHAR(128)    NOT NULL COMMENT '用户填写的抖音号（规范化）',
  `douyin_id_raw`    VARCHAR(128)    DEFAULT NULL COMMENT '用户填写的抖音号（原始）',
  `member_code_input` VARCHAR(64)    NOT NULL COMMENT '用户填写的会员编码（规范化）',
  `claim_months`     INT UNSIGNED    NOT NULL COMMENT '用户填写的已订阅月数',

  `status`           VARCHAR(16)     NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW/MATCHED/REJECTED/UNBINDING',
  `submit_ip`        VARBINARY(16)   DEFAULT NULL COMMENT '提交 IP（零成本留痕，不做实时拦截）',
  `submit_device`    VARCHAR(64)     DEFAULT NULL COMMENT '提交设备指纹',

  `matched_at`       DATETIME(3)     DEFAULT NULL COMMENT '自动通过/审核通过时间',
  `reviewer_id`      BIGINT UNSIGNED DEFAULT NULL COMMENT '审核人（RuoYi sys_user.id）',
  `review_time`      DATETIME(3)     DEFAULT NULL COMMENT '审核时间',
  `reject_reason`    VARCHAR(255)    DEFAULT NULL COMMENT '驳回原因',

  `unbind_apply_at`  DATETIME(3)     DEFAULT NULL COMMENT '解绑申请时间',
  `unbind_at`        DATETIME(3)     DEFAULT NULL COMMENT '解绑完成时间',

  `create_time`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_binding_account` (`account_id`) COMMENT '★一账号一会员（D-6）',
  UNIQUE KEY `uk_kjx_binding_member` (`member_id`)   COMMENT '★一会员一账号（D-2）',
  KEY `idx_kjx_binding_dept_status` (`dept_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号-会员绑定';

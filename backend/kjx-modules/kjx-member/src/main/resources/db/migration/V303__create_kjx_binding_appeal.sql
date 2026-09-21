-- WP-11 / V303：绑定/解绑申诉。
CREATE TABLE `kjx_binding_appeal` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `account_id`  BIGINT UNSIGNED NOT NULL COMMENT '账号ID',
  `binding_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '关联绑定记录',
  `appeal_type` VARCHAR(32)     NOT NULL COMMENT 'CHANGE_DOUYIN/WRONG_BIND/OTHER',
  `content`     VARCHAR(1000)   NOT NULL COMMENT '申诉说明',
  `contact`     VARCHAR(128)    DEFAULT NULL COMMENT '联系方式',
  `attachments` JSON            DEFAULT NULL COMMENT '凭证图片 URL 数组',
  `status`      VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `handler_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '处理人',
  `handle_time` DATETIME(3)     DEFAULT NULL COMMENT '处理时间',
  `handle_note` VARCHAR(500)    DEFAULT NULL COMMENT '处理说明',
  `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_kjx_appeal_status` (`status`, `create_time`),
  KEY `idx_kjx_appeal_account` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绑定申诉';

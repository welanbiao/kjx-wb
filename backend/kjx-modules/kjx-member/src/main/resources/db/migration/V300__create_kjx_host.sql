-- WP-11 / V300：主播扩展表。主键 = sys_dept.id（1:1），对齐 docs/09 §2.2.1。
CREATE TABLE `kjx_host` (
  `dept_id`     BIGINT UNSIGNED NOT NULL COMMENT '★主播ID（= sys_dept.id，与部门 1:1）',
  `host_code`   VARCHAR(32)     NOT NULL COMMENT '主播短码（用于绑定链接与会员编码前缀）',
  `dy_account`  VARCHAR(128)    DEFAULT NULL COMMENT '主播抖音号',
  `logo_url`    VARCHAR(512)    DEFAULT NULL COMMENT '头像/Logo',
  `contact`     VARCHAR(255)    DEFAULT NULL COMMENT '客服联系方式',
  `remark`      VARCHAR(255)    DEFAULT NULL COMMENT '备注',
  `creator`     VARCHAR(64)     DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater`     VARCHAR(64)     DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`     BIT(1)          NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`dept_id`),
  UNIQUE KEY `uk_kjx_host_code` (`host_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='主播扩展（对应 sys_dept 的一个部门）';

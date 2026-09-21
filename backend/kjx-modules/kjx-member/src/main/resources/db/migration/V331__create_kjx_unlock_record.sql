-- WP-13 / V331：解锁记录快照，永久保留，不逻辑删除。字段对齐 docs/09 §2.6（含 source、dept_id）。
-- 失败记录不在本表加窗口唯一键；高频失败由 Redis 窗口合并后再落库。
CREATE TABLE `kjx_unlock_record` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `dept_id`               BIGINT UNSIGNED NOT NULL COMMENT '★主播部门快照；数据权限直接作用于本表',
  `member_id`             BIGINT UNSIGNED NOT NULL COMMENT '会员ID',
  `member_code`           VARCHAR(64)     NOT NULL COMMENT '会员编码（冗余）',
  `douyin_id`             VARCHAR(128)    DEFAULT NULL COMMENT '抖音号（冗余）',
  `account_id`            BIGINT UNSIGNED DEFAULT NULL COMMENT '操作账号',

  `work_id`               BIGINT UNSIGNED NOT NULL COMMENT '作品ID',
  `work_name`             VARCHAR(128)    DEFAULT NULL COMMENT '作品名称（冗余）',
  `work_version`          VARCHAR(32)     DEFAULT NULL COMMENT '作品版本',

  `unlock_time`           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '解锁时间',
  `before_unlocked_count` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '解锁前已解锁数量（快照）',
  `after_unlocked_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '解锁后已解锁数量（快照）',
  `before_remain_count`   INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '解锁前剩余可解锁数（快照）',
  `after_remain_count`    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '解锁后剩余可解锁数（快照）',

  `result`                VARCHAR(16)     NOT NULL COMMENT 'SUCCESS / FAILED',
  `fail_reason`           VARCHAR(64)     DEFAULT NULL COMMENT '失败原因码',
  `source`                VARCHAR(24)     NOT NULL DEFAULT 'user' COMMENT '★user/official_add/official_remove/fix（M-7）',
  `operator_id`           BIGINT UNSIGNED DEFAULT NULL COMMENT '运营操作人',
  `request_fingerprint`   VARCHAR(128)    DEFAULT NULL COMMENT 'IP + 设备指纹 + 请求ID',
  `idempotency_key`       VARCHAR(64)     DEFAULT NULL COMMENT '幂等键',
  `create_time`           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

  PRIMARY KEY (`id`),
  KEY `idx_kjx_unlock_dept_member` (`dept_id`, `member_id`, `unlock_time`),
  KEY `idx_kjx_unlock_work` (`work_id`, `unlock_time`),
  KEY `idx_kjx_unlock_result` (`result`, `unlock_time`),
  KEY `idx_kjx_unlock_source` (`source`, `unlock_time`),
  KEY `idx_kjx_unlock_operator` (`operator_id`, `unlock_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='解锁记录（快照，永久保留）';

-- WP-30 / V510：钱包只作行锁锚点，不保存余额。
CREATE TABLE `kjx_wallet` (
  `member_id`   BIGINT UNSIGNED NOT NULL,
  `dept_id`     BIGINT UNSIGNED NOT NULL,
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`member_id`),
  KEY `idx_kjx_wallet_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计费行锁锚点';

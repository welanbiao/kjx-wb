-- WP-50 / V700：审计日志（WORM + 链式哈希）。对齐 docs/06 §4.3 / §12.1。
-- 不分区（MySQL 唯一键须含分区列）。仅追加；链式哈希在写入时计算。
CREATE TABLE `log_audit` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `log_id`          CHAR(36)        NOT NULL COMMENT '日志 UUID',
  `ts`              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `trace_id`        VARCHAR(64)     NOT NULL,
  `request_id`      VARCHAR(64)     NOT NULL,
  `admin_id`        BIGINT UNSIGNED NOT NULL,
  `admin_name`      VARCHAR(64)     NOT NULL,
  `roles`           JSON            NOT NULL,
  `ip`              VARBINARY(16)   NOT NULL,
  `ip_region`       VARCHAR(64)     DEFAULT NULL,
  `action`          VARCHAR(64)     NOT NULL,
  `target_type`     VARCHAR(32)     NOT NULL,
  `target_id`       VARCHAR(64)     NOT NULL,
  `before_json`     JSON            DEFAULT NULL,
  `after_json`      JSON            DEFAULT NULL,
  `result`          VARCHAR(16)     NOT NULL,
  `reason`          VARCHAR(256)    DEFAULT NULL,
  `prev_hash`       CHAR(64)        NOT NULL,
  `hash`            CHAR(64)        NOT NULL,
  `schema_version`  VARCHAR(8)      NOT NULL DEFAULT '1.0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_log_audit_log_id` (`log_id`),
  KEY `idx_log_audit_admin_ts` (`admin_id`, `ts`),
  KEY `idx_log_audit_target` (`target_type`, `target_id`),
  KEY `idx_log_audit_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志 WORM';

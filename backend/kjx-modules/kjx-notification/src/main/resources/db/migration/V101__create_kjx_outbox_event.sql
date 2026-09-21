-- WP-02 / V101：事务型 Outbox。字段与索引严格对齐 docs/13 §3.5。
-- 禁止增加 locked_at / locked_by 等未冻结列。PROCESSING 租约使用 next_retry_at。
CREATE TABLE `kjx_outbox_event` (
  `event_id`        CHAR(36)       NOT NULL,
  `aggregate_type`  VARCHAR(32)    NOT NULL,
  `aggregate_id`    VARCHAR(64)    NOT NULL,
  `event_type`      VARCHAR(64)    NOT NULL,
  `payload`         JSON           NOT NULL,
  `status`          VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
  `retry_count`     INT UNSIGNED   NOT NULL DEFAULT 0,
  `next_retry_at`   DATETIME(3)    DEFAULT NULL,
  `last_error`      VARCHAR(500)   DEFAULT NULL,
  `create_time`     DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `published_at`    DATETIME(3)    DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  KEY `idx_outbox_poll` (`status`, `next_retry_at`, `create_time`),
  KEY `idx_outbox_aggregate` (`aggregate_type`, `aggregate_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- WP-02 / V102：消费幂等回执（event_id + channel）。非 docs/13 冻结表，不改 kjx_outbox_event 列。
-- 语义见 docs/adr/ADR-001-outbox-consume-receipt.md：PROCESSING 带租约，崩溃后可回收；DONE 永不重复。
-- 外部 SMS 仍是至少一次，向支持幂等的提供方传 eventId，不声称 exactly-once。
CREATE TABLE `kjx_outbox_consume_receipt` (
  `event_id`       CHAR(36)       NOT NULL,
  `channel`        VARCHAR(32)    NOT NULL,
  `status`         VARCHAR(16)    NOT NULL DEFAULT 'PROCESSING',
  `attempt_count`  INT UNSIGNED   NOT NULL DEFAULT 0,
  `lease_until`    DATETIME(3)    DEFAULT NULL,
  `create_time`    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time`    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`event_id`, `channel`),
  KEY `idx_receipt_lease` (`status`, `lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

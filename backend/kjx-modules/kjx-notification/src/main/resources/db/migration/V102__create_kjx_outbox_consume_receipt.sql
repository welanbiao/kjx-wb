-- WP-02 / V102：消费幂等回执（event_id + channel）。非 docs/13 冻结表，不改 Outbox 列。
-- 用于至少一次投递下的重复消费去重。
CREATE TABLE `kjx_outbox_consume_receipt` (
  `event_id`    CHAR(36)       NOT NULL,
  `channel`     VARCHAR(32)    NOT NULL,
  `status`      VARCHAR(16)    NOT NULL DEFAULT 'DONE',
  `create_time` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`event_id`, `channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

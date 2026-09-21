-- WP-30 / V512：append-only 账本。余额只能由流水派生。
CREATE TABLE `kjx_ledger_entry` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `member_id`        BIGINT UNSIGNED NOT NULL,
  `dept_id`          BIGINT UNSIGNED NOT NULL,
  `biz_type`         VARCHAR(32)     NOT NULL,
  `direction`        VARCHAR(8)      NOT NULL,
  `amount`           DECIMAL(18,6)   NOT NULL,
  `balance_after`    DECIMAL(18,6)   NOT NULL,
  `ref_type`         VARCHAR(32)     NOT NULL,
  `ref_id`           VARCHAR(64)     NOT NULL,
  `idempotency_key`  VARCHAR(64)     NOT NULL,
  `payload_hash`     CHAR(64)        NOT NULL,
  `reason`           VARCHAR(200)    NOT NULL,
  `created_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_ledger_entry_idem` (`idempotency_key`),
  KEY `idx_kjx_ledger_entry_member_created` (`member_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='append-only 账本';

CREATE TRIGGER `trg_kjx_ledger_entry_block_update`
BEFORE UPDATE ON `kjx_ledger_entry`
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'kjx_ledger_entry is append-only';

CREATE TRIGGER `trg_kjx_ledger_entry_block_delete`
BEFORE DELETE ON `kjx_ledger_entry`
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'kjx_ledger_entry is append-only';

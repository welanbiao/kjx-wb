-- WP-30 / V500：BYOK 凭据只存 secret_ref，禁止明文 Key 列。
CREATE TABLE `kjx_channel_credential` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `member_id`      BIGINT UNSIGNED NOT NULL,
  `dept_id`        BIGINT UNSIGNED NOT NULL,
  `provider`       VARCHAR(32)     NOT NULL,
  `secret_ref`     VARCHAR(128)    NOT NULL,
  `endpoint_host`  VARCHAR(255)    NOT NULL,
  `status`         VARCHAR(16)     NOT NULL,
  `created_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_channel_credential_member_provider` (`member_id`, `provider`),
  KEY `idx_kjx_channel_credential_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='BYOK 凭据引用';

-- WP-50 / V706：归档清单与校验哈希。
CREATE TABLE `log_archive_manifest` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `category`        VARCHAR(32)     NOT NULL,
  `dt_from`         DATE            NOT NULL,
  `dt_to`           DATE            NOT NULL,
  `row_count`       BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `object_key`      VARCHAR(512)    NOT NULL,
  `checksum_sha256` CHAR(64)        NOT NULL,
  `create_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_log_archive_cat_dt` (`category`, `dt_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='日志归档清单';

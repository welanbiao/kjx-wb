-- WP-10 / V202：验证码发送审计。校验走 Redis 哈希，本表不存明文验证码或手机号。
CREATE TABLE `kjx_verify_code` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `target_hash`   CHAR(64)        NOT NULL COMMENT '手机号的 HMAC',
  `target_mask`   VARCHAR(64)     DEFAULT NULL COMMENT '掩码展示（138****8888）',
  `scene`         VARCHAR(32)     NOT NULL COMMENT 'register/login/reset_pwd/change_pwd/cancel',
  `code_hash`     CHAR(64)        NOT NULL COMMENT '验证码哈希（不存明文）',
  `send_ip`       VARBINARY(16)   DEFAULT NULL COMMENT '发送 IP',
  `channel`       VARCHAR(16)     NOT NULL DEFAULT 'SMS' COMMENT 'SMS（M1唯一通道）',
  `verify_result` TINYINT         DEFAULT NULL COMMENT 'NULL未校验 1成功 0失败',
  `expire_at`     DATETIME(3)     NOT NULL COMMENT '过期时间',
  `create_time`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_kjx_code_target_scene` (`target_hash`, `scene`, `create_time`),
  KEY `idx_kjx_code_send_ip` (`send_ip`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='验证码发送记录';

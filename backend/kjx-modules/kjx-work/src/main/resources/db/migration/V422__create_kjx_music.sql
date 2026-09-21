-- WP-31 / V422：平台音乐库。BGM 不进作品包。
CREATE TABLE `kjx_music_track` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `track_code`      VARCHAR(64)     NOT NULL COMMENT '曲目业务键',
  `title`           VARCHAR(128)    NOT NULL COMMENT '标题',
  `artist`          VARCHAR(128)    DEFAULT NULL COMMENT '艺术家',
  `album`           VARCHAR(128)    DEFAULT NULL COMMENT '专辑',
  `duration_sec`    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '时长秒',
  `format`          VARCHAR(16)     DEFAULT NULL COMMENT 'mp3/ogg/aac',
  `bitrate`         INT UNSIGNED    DEFAULT NULL COMMENT '码率',
  `mood_tags`       VARCHAR(255)    DEFAULT NULL COMMENT '情绪标签',
  `scene_tags`      VARCHAR(255)    DEFAULT NULL COMMENT '场景标签',
  `license`         VARCHAR(255)    NOT NULL COMMENT '版权来源（必填）',
  `volume_default`  INT             NOT NULL DEFAULT 100 COMMENT '默认音量 0-100',
  `fade_in_ms`      INT             NOT NULL DEFAULT 800 COMMENT '淡入毫秒',
  `fade_out_ms`     INT             NOT NULL DEFAULT 800 COMMENT '淡出毫秒',
  `watermark_id`    VARCHAR(64)     DEFAULT NULL COMMENT '隐形水印ID',
  `storage_key`     VARCHAR(512)    DEFAULT NULL COMMENT '音频对象键',
  `sha256`          CHAR(64)        DEFAULT NULL COMMENT '音频哈希',
  `size_bytes`      BIGINT UNSIGNED DEFAULT NULL COMMENT '音频字节',
  `creator`         VARCHAR(64)     DEFAULT '' COMMENT '创建者',
  `create_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater`         VARCHAR(64)     DEFAULT '' COMMENT '更新者',
  `update_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_music_track_code` (`track_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='音乐曲目';

CREATE TABLE `kjx_music_pack` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `pack_code`   VARCHAR(64)     NOT NULL COMMENT '音乐包业务键',
  `name`        VARCHAR(128)    NOT NULL COMMENT '名称',
  `summary`     VARCHAR(500)    DEFAULT NULL COMMENT '简介',
  `creator`     VARCHAR(64)     DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_music_pack_code` (`pack_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='音乐包';

CREATE TABLE `kjx_music_pack_track` (
  `pack_id`  BIGINT UNSIGNED NOT NULL COMMENT '音乐包ID',
  `track_id` BIGINT UNSIGNED NOT NULL COMMENT '曲目ID',
  PRIMARY KEY (`pack_id`, `track_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='音乐包曲目';

CREATE TABLE `kjx_work_music_binding` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `work_id`     BIGINT UNSIGNED NOT NULL COMMENT '作品ID',
  `version`     VARCHAR(32)     NOT NULL COMMENT '作品版本',
  `scene_key`   VARCHAR(64)     NOT NULL COMMENT '场景 key',
  `rule_json`   JSON            NOT NULL COMMENT '选曲规则',
  `creator`     VARCHAR(64)     DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kjx_work_music_binding` (`work_id`, `version`, `scene_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作品场景选曲';

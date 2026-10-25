-- WP-B：分手结局修改器上架。避开 V620–V632。可玩对姓名与计划「可玩示例是」句一致（旸 = U+65F8）。
INSERT INTO kjx_work (
  work_code, name, work_type, summary, cover_url, screenshots, category, tags,
  status, current_version, content_version, access_form, prompt_mode, allowed_channels,
  allow_byok_direct, apps, tools, server_tools, pricing_mode, gray_ratio, sort_weight,
  unlock_count, publish_time, creator, updater, deleted, tenant_id
)
SELECT
  'breakup-editor',
  '分手结局修改器',
  'game',
  '如果那句话没说出口，他们还会分手吗？今天，帮 TA 改写曾经的选择。',
  NULL,
  CAST('[]' AS JSON),
  'game',
  '文游,七夕',
  'PUBLISHED',
  '1.0.0',
  '1.0.0',
  'A',
  'SERVER',
  'PLATFORM,BYOK',
  b'0',
  CAST('[]' AS JSON),
  CAST('["persona.assemble","chat.send","chat.group.send","redpacket.send"]' AS JSON),
  CAST('[]' AS JSON),
  'per_turn',
  0,
  100,
  0,
  CURRENT_TIMESTAMP(3),
  'wp-b',
  'wp-b',
  b'0',
  0
WHERE NOT EXISTS (
  SELECT 1 FROM kjx_work WHERE work_code = 'breakup-editor' AND deleted = b'0'
);

INSERT INTO kjx_work_version (
  work_id, version, status, form, runtime_compat, entry, gray_ratio, publish_time, creator, updater
)
SELECT w.id, '1.0.0', 'PUBLISHED', 'A', 'runtime-1', '/runtime/breakup-editor', 0, CURRENT_TIMESTAMP(3), 'wp-b', 'wp-b'
  FROM kjx_work w
 WHERE w.work_code = 'breakup-editor' AND w.deleted = b'0'
   AND NOT EXISTS (
     SELECT 1 FROM kjx_work_version v WHERE v.work_id = w.id AND v.version = '1.0.0'
   );

INSERT INTO kjx_content_version (
  work_id, version, status, node_count, content_url, content, review_time, publish_time, creator, updater
)
SELECT w.id, '1.0.0', 'PUBLISHED', 15, NULL, CAST('{"coupleId":"asphyxia"}' AS JSON), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 'wp-b', 'wp-b'
  FROM kjx_work w
 WHERE w.work_code = 'breakup-editor' AND w.deleted = b'0'
   AND NOT EXISTS (
     SELECT 1 FROM kjx_content_version c WHERE c.work_id = w.id AND c.version = '1.0.0'
   );

INSERT INTO kjx_work_package (
  work_id, version, status, form, cdn_prefix, server_prefix, content_version, gray_ratio, creator, updater
)
SELECT w.id,
       '1.0.0',
       'PUBLISHED',
       'A',
       'cdn/works/breakup-editor/1.0.0/cdn',
       'cdn/works/breakup-editor/1.0.0/server',
       '1.0.0',
       0,
       'wp-b',
       'wp-b'
  FROM kjx_work w
 WHERE w.work_code = 'breakup-editor' AND w.deleted = b'0'
   AND NOT EXISTS (
     SELECT 1 FROM kjx_work_package p WHERE p.work_id = w.id AND p.version = '1.0.0'
   );

INSERT INTO kjx_asset (
  work_id, version, asset_key, path, content_type, size_bytes, sha256, storage_key, scope, tags, license, creator
)
SELECT w.id, '1.0.0', 'font-serif', 'assets/fonts/noto-serif-sc-600-subset.woff2', 'font/woff2', 0,
       REPEAT('0', 64), 'cdn/works/breakup-editor/1.0.0/cdn/assets/fonts/noto-serif-sc-600-subset.woff2',
       'CDN', 'font', 'subset', 'wp-b'
  FROM kjx_work w
 WHERE w.work_code = 'breakup-editor' AND w.deleted = b'0'
   AND NOT EXISTS (
     SELECT 1 FROM kjx_asset a WHERE a.work_id = w.id AND a.version = '1.0.0' AND a.asset_key = 'font-serif'
   );

INSERT INTO kjx_music_track (
  track_code, title, artist, duration_sec, format, mood_tags, scene_tags, license, storage_key, sha256, size_bytes, creator
)
SELECT 'breakup-home-bgm', 'Sea of Hate, Sky of Love', '恨海情天', 0, 'mp3', 'home', 'home',
       'original-pack', 'cdn/works/breakup-editor/1.0.0/cdn/assets/audio/home-bgm.mp3', REPEAT('0', 64), 0, 'wp-b'
WHERE NOT EXISTS (SELECT 1 FROM kjx_music_track WHERE track_code = 'breakup-home-bgm');

INSERT INTO kjx_music_track (
  track_code, title, artist, duration_sec, format, mood_tags, scene_tags, license, storage_key, sha256, size_bytes, creator
)
SELECT 'breakup-prologue-bgm', 'Prologue', '恨海情天', 0, 'mp3', 'prologue', 'prologue',
       'original-pack', 'cdn/works/breakup-editor/1.0.0/cdn/assets/audio/prologue-bgm.mp3', REPEAT('0', 64), 0, 'wp-b'
WHERE NOT EXISTS (SELECT 1 FROM kjx_music_track WHERE track_code = 'breakup-prologue-bgm');

INSERT INTO kjx_work_music_binding (work_id, version, scene_key, rule_json, creator)
SELECT w.id, '1.0.0', 'home', CAST('{"trackCode":"breakup-home-bgm"}' AS JSON), 'wp-b'
  FROM kjx_work w
 WHERE w.work_code = 'breakup-editor' AND w.deleted = b'0'
   AND NOT EXISTS (
     SELECT 1 FROM kjx_work_music_binding b WHERE b.work_id = w.id AND b.version = '1.0.0' AND b.scene_key = 'home'
   );

INSERT INTO kjx_work_music_binding (work_id, version, scene_key, rule_json, creator)
SELECT w.id, '1.0.0', 'prologue', CAST('{"trackCode":"breakup-prologue-bgm"}' AS JSON), 'wp-b'
  FROM kjx_work w
 WHERE w.work_code = 'breakup-editor' AND w.deleted = b'0'
   AND NOT EXISTS (
     SELECT 1 FROM kjx_work_music_binding b WHERE b.work_id = w.id AND b.version = '1.0.0' AND b.scene_key = 'prologue'
   );

INSERT INTO kjx_oss_object (scope, object_key, size_bytes, sha256, content_type, work_id, version)
SELECT 'CDN', 'cdn/works/breakup-editor/1.0.0/cdn/assets/audio/home-bgm.mp3', 0, REPEAT('0', 64), 'audio/mpeg', w.id, '1.0.0'
  FROM kjx_work w
 WHERE w.work_code = 'breakup-editor' AND w.deleted = b'0'
   AND NOT EXISTS (
     SELECT 1 FROM kjx_oss_object o WHERE o.scope = 'CDN' AND o.object_key = 'cdn/works/breakup-editor/1.0.0/cdn/assets/audio/home-bgm.mp3'
   );

INSERT INTO kjx_oss_object (scope, object_key, size_bytes, sha256, content_type, work_id, version)
SELECT 'CDN', 'cdn/works/breakup-editor/1.0.0/cdn/assets/audio/prologue-bgm.mp3', 0, REPEAT('0', 64), 'audio/mpeg', w.id, '1.0.0'
  FROM kjx_work w
 WHERE w.work_code = 'breakup-editor' AND w.deleted = b'0'
   AND NOT EXISTS (
     SELECT 1 FROM kjx_oss_object o WHERE o.scope = 'CDN' AND o.object_key = 'cdn/works/breakup-editor/1.0.0/cdn/assets/audio/prologue-bgm.mp3'
   );

INSERT INTO kjx_oss_object (scope, object_key, size_bytes, sha256, content_type, work_id, version)
SELECT 'SERVER', 'cdn/works/breakup-editor/1.0.0/server/trial-script.json', 0, REPEAT('0', 64), 'application/json', w.id, '1.0.0'
  FROM kjx_work w
 WHERE w.work_code = 'breakup-editor' AND w.deleted = b'0'
   AND NOT EXISTS (
     SELECT 1 FROM kjx_oss_object o WHERE o.scope = 'SERVER' AND o.object_key = 'cdn/works/breakup-editor/1.0.0/server/trial-script.json'
   );

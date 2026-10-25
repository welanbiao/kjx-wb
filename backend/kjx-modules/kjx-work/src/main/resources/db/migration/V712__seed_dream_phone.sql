-- WP-C：梦女小手机上架。已存在则跳过。
INSERT INTO kjx_work (
  work_code, name, work_type, summary, cover_url, screenshots, category, tags,
  status, current_version, content_version, access_form, prompt_mode, allowed_channels,
  allow_byok_direct, apps, tools, server_tools, pricing_mode, gray_ratio, sort_weight,
  unlock_count, publish_time, creator, updater, deleted, tenant_id
)
SELECT
  'dream-phone',
  '梦女小手机',
  'game',
  '一对一、朋友圈、红包都在这部手机里。',
  NULL,
  CAST('[]' AS JSON),
  'game',
  '文游,微信',
  'PUBLISHED',
  '1.0.0',
  '1.0.0',
  'A',
  'SERVER',
  'PLATFORM,BYOK',
  b'0',
  CAST('[]' AS JSON),
  CAST('["persona.assemble","chat.send","chat.group.send","redpacket.send","moments.post"]' AS JSON),
  CAST('[]' AS JSON),
  'per_turn',
  0,
  90,
  0,
  CURRENT_TIMESTAMP(3),
  'wp-c',
  'wp-c',
  b'0',
  0
WHERE NOT EXISTS (
  SELECT 1 FROM kjx_work WHERE work_code = 'dream-phone' AND deleted = b'0'
);

INSERT INTO kjx_work_version (
  work_id, version, status, form, runtime_compat, entry, gray_ratio, publish_time, creator, updater
)
SELECT w.id, '1.0.0', 'PUBLISHED', 'A', 'runtime-1', '/runtime/dream-phone', 0, CURRENT_TIMESTAMP(3), 'wp-c', 'wp-c'
  FROM kjx_work w
 WHERE w.work_code = 'dream-phone' AND w.deleted = b'0'
   AND NOT EXISTS (
     SELECT 1 FROM kjx_work_version v WHERE v.work_id = w.id AND v.version = '1.0.0'
   );

INSERT INTO kjx_content_version (
  work_id, version, status, node_count, content_url, content, review_time, publish_time, creator, updater
)
SELECT w.id, '1.0.0', 'PUBLISHED', 1, NULL, CAST('{"player":"梦女","character":"顾西"}' AS JSON), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 'wp-c', 'wp-c'
  FROM kjx_work w
 WHERE w.work_code = 'dream-phone' AND w.deleted = b'0'
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
       'cdn/works/dream-phone/1.0.0/cdn',
       'cdn/works/dream-phone/1.0.0/server',
       '1.0.0',
       0,
       'wp-c',
       'wp-c'
  FROM kjx_work w
 WHERE w.work_code = 'dream-phone' AND w.deleted = b'0'
   AND NOT EXISTS (
     SELECT 1 FROM kjx_work_package p WHERE p.work_id = w.id AND p.version = '1.0.0'
   );

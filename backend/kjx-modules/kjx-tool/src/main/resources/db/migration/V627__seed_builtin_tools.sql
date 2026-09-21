-- WP-41 / V627：平台 builtin 工具样例。handler 与 Java BuiltinCatalog 键一致。
INSERT INTO `kjx_tool` (`code`, `type`, `display_name`, `enabled`) VALUES
('time.get', 'builtin', '获取时间', 1),
('weather.get', 'builtin', '获取天气', 1),
('save.checkpoint', 'builtin', '存档检查点', 1),
('session.end', 'builtin', '结束会话', 1),
('wallet.adjust', 'builtin', '调整钱包', 1),
('moments.post', 'builtin', '发朋友圈', 1),
('chat.send', 'builtin', '发送聊天', 1),
('redpacket.send', 'builtin', '发红包', 1),
('memo.write', 'builtin', '写备忘', 1),
('music.play', 'builtin', '播放音乐', 1);

INSERT INTO `kjx_tool_version`
  (`tool_id`, `version`, `description`, `parameters_schema`, `preconditions`, `implementation`,
   `permissions`, `rate_limit`, `side_effects`, `audit`, `timeout_ms`, `retry_max`, `status`)
SELECT t.id, '1.0.0', v.description, CAST(v.parameters_schema AS JSON), CAST(v.preconditions AS JSON),
       CAST(v.implementation AS JSON), CAST(v.permissions AS JSON), CAST(v.rate_limit AS JSON),
       CAST(v.side_effects AS JSON), 1, 5000, 0, 'ACTIVE'
FROM `kjx_tool` t
JOIN (
  SELECT 'time.get' AS code, '返回真实时间' AS description,
         '{"type":"object","properties":{},"additionalProperties":false}' AS parameters_schema,
         '{}' AS preconditions,
         '{"kind":"builtin","handler":"time.get"}' AS implementation,
         '["time.real"]' AS permissions,
         '{"perSession":100,"cooldownSec":0}' AS rate_limit,
         '[]' AS side_effects
  UNION ALL
  SELECT 'weather.get', '返回天气占位',
         '{"type":"object","properties":{},"additionalProperties":false}',
         '{}',
         '{"kind":"builtin","handler":"weather.get"}',
         '["time.real"]',
         '{"perSession":50,"cooldownSec":0}',
         '[]'
  UNION ALL
  SELECT 'save.checkpoint', '写入会话检查点',
         '{"type":"object","properties":{"reason":{"type":"string","maxLength":128}},"additionalProperties":false}',
         '{}',
         '{"kind":"builtin","handler":"save.checkpoint"}',
         '["state.write"]',
         '{"perSession":30,"cooldownSec":1}',
         '["state"]'
  UNION ALL
  SELECT 'session.end', '结束当前会话',
         '{"type":"object","properties":{"reason":{"type":"string","maxLength":128}},"additionalProperties":false}',
         '{}',
         '{"kind":"builtin","handler":"session.end"}',
         '["state.write"]',
         '{"perSession":10,"cooldownSec":0}',
         '["state"]'
  UNION ALL
  SELECT 'wallet.adjust', '调整会话钱包余额',
         '{"type":"object","properties":{"delta":{"type":"number"},"reason":{"type":"string","maxLength":128}},"required":["delta"],"additionalProperties":false}',
         '{}',
         '{"kind":"builtin","handler":"wallet.adjust"}',
         '["state.write","ui.render"]',
         '{"perSession":40,"cooldownSec":0}',
         '["state","ui"]'
  UNION ALL
  SELECT 'moments.post', '发布朋友圈',
         '{"type":"object","properties":{"content":{"type":"string","maxLength":2000},"visibility":{"type":"string","enum":["public","friends","private"]}},"required":["content"],"additionalProperties":false}',
         '{}',
         '{"kind":"builtin","handler":"moments.post"}',
         '["ui.render","state.write"]',
         '{"perSession":20,"perDay":50,"cooldownSec":2}',
         '["state","ui"]'
  UNION ALL
  SELECT 'chat.send', '发送聊天消息',
         '{"type":"object","properties":{"content":{"type":"string","maxLength":4000},"delaySec":{"type":"integer","minimum":0,"maximum":60}},"required":["content"],"additionalProperties":false}',
         '{}',
         '{"kind":"builtin","handler":"chat.send"}',
         '["ui.render","state.write"]',
         '{"perSession":80,"cooldownSec":0}',
         '["state","ui"]'
  UNION ALL
  SELECT 'redpacket.send', '发送红包卡片',
         '{"type":"object","properties":{"amount":{"type":"number","minimum":0.01},"note":{"type":"string","maxLength":128}},"required":["amount"],"additionalProperties":false}',
         '{}',
         '{"kind":"builtin","handler":"redpacket.send"}',
         '["state.write","ui.render"]',
         '{"perSession":20,"cooldownSec":1}',
         '["state","ui"]'
  UNION ALL
  SELECT 'memo.write', '写入备忘',
         '{"type":"object","properties":{"title":{"type":"string","maxLength":128},"body":{"type":"string","maxLength":4000}},"required":["title"],"additionalProperties":false}',
         '{}',
         '{"kind":"builtin","handler":"memo.write"}',
         '["state.write","ui.render"]',
         '{"perSession":30,"cooldownSec":0}',
         '["state","ui"]'
  UNION ALL
  SELECT 'music.play', '播放指定曲目',
         '{"type":"object","properties":{"trackId":{"type":"string","maxLength":64},"fadeIn":{"type":"boolean"},"loop":{"type":"boolean"}},"required":["trackId"],"additionalProperties":false}',
         '{}',
         '{"kind":"builtin","handler":"music.play"}',
         '["ui.render"]',
         '{"perSession":40,"cooldownSec":0}',
         '["ui"]'
) v ON v.code = t.code;

-- WP-41 / V629：共用 L2 工具 persona.assemble、chat.group.send；chat.bubble 增加可选 speaker。
UPDATE `kjx_ui_node_type`
SET `props_schema` = CAST('{"type":"object","properties":{"text":{"type":"string","maxLength":4000},"role":{"type":"string","enum":["npc","user","system"]},"speaker":{"type":"string","maxLength":64}},"required":["text"],"additionalProperties":false}' AS JSON)
WHERE `type` = 'chat.bubble';

INSERT INTO `kjx_tool` (`code`, `type`, `display_name`, `enabled`) VALUES
('persona.assemble', 'builtin', '组装人设', 1),
('chat.group.send', 'builtin', '发送群聊', 1);

INSERT INTO `kjx_tool_version`
  (`tool_id`, `version`, `description`, `parameters_schema`, `preconditions`, `implementation`,
   `permissions`, `rate_limit`, `side_effects`, `audit`, `timeout_ms`, `retry_max`, `status`)
SELECT t.id, '1.0.0', v.description, CAST(v.parameters_schema AS JSON), CAST(v.preconditions AS JSON),
       CAST(v.implementation AS JSON), CAST(v.permissions AS JSON), CAST(v.rate_limit AS JSON),
       CAST(v.side_effects AS JSON), 1, 5000, 0, 'ACTIVE'
FROM `kjx_tool` t
JOIN (
  SELECT 'persona.assemble' AS code, '从结构化人设组装模型片段，不写入 UI' AS description,
         '{"type":"object","properties":{},"additionalProperties":false}' AS parameters_schema,
         '{}' AS preconditions,
         '{"kind":"builtin","handler":"persona.assemble"}' AS implementation,
         '["state.write"]' AS permissions,
         '{"perSession":40,"cooldownSec":0}' AS rate_limit,
         '["state"]' AS side_effects
  UNION ALL
  SELECT 'chat.group.send', '发送群聊消息',
         '{"type":"object","properties":{"content":{"type":"string","maxLength":4000},"speaker":{"type":"string","maxLength":64}},"required":["content","speaker"],"additionalProperties":false}',
         '{}',
         '{"kind":"builtin","handler":"chat.group.send"}',
         '["ui.render","state.write"]',
         '{"perSession":80,"cooldownSec":0}',
         '["state","ui"]'
) v ON v.code = t.code;

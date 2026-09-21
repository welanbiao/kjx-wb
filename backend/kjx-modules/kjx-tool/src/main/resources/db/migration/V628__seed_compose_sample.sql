-- WP-41 / V628：声明式组合样例。明确 steps 列表，不含 script。
INSERT INTO `kjx_tool` (`code`, `type`, `display_name`, `enabled`) VALUES
('metric.adjust', 'custom', '调整指标', 1);

INSERT INTO `kjx_tool_version`
  (`tool_id`, `version`, `description`, `parameters_schema`, `preconditions`, `implementation`,
   `permissions`, `rate_limit`, `side_effects`, `audit`, `timeout_ms`, `retry_max`, `status`)
SELECT t.id, '1.0.0',
       '按声明式步骤调整会话指标，单次不超过 15',
       CAST('{"type":"object","properties":{"delta":{"type":"number"},"label":{"type":"string","maxLength":64}},"required":["delta"],"additionalProperties":false}' AS JSON),
       CAST('{}' AS JSON),
       CAST('{"kind":"compose","steps":[{"op":"assert","cond":{"path":"$.args.delta","lte":15},"message":"单次调整过大"},{"op":"assert","cond":{"path":"$.args.delta","gte":-15},"message":"单次调整过大"},{"op":"state.patch","value":{"metrics":{"lastDelta":"$.args.delta","label":"$.args.label"}}},{"op":"ui.render","target":"toast","uiOp":"append","node":{"type":"toast","props":{"text":"指标已更新"}}},{"op":"return","value":{"ok":true}}]}' AS JSON),
       CAST('["state.write","ui.render"]' AS JSON),
       CAST('{"perSession":30,"cooldownSec":0}' AS JSON),
       CAST('["state","ui"]' AS JSON),
       1, 5000, 0, 'ACTIVE'
FROM `kjx_tool` t
WHERE t.code = 'metric.adjust';

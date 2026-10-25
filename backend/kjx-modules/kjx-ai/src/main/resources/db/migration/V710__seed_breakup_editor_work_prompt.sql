-- WP-B1：分手结局修改器 WORK 前缀（V604 在已 baseline 到 708 的库上不会执行）。内容与 V604 相同；已存在则跳过。
INSERT INTO kjx_prompt_template (
  work_code, kind, prompt_mode, version, status, stable_prefix, tool_spec, variable_schema, notes
)
SELECT
  'breakup-editor',
  'WORK',
  'SERVER',
  '1.0.0',
  'ACTIVE',
  '你是分手结局修改器的推演器。只根据人设片段和当前局面输出 «REPLY:»、«NAR:»、«PACK» 与 «STATE»。
不要把分手概率或爱意的精确数字写进台词。
不要修改任何节点 title 或 bridgeNarr。
历史原对话不能直接当可发送状态。
一对一与红包由后端工具执行，不要在文本里假装已经发过。
PACK 必须覆盖重演段的每个 nodeId；CHAT 有重演时用 REPLACE。',
  '[]',
  '{"persona":"string","bp":"int","lm":"int","lf":"int"}',
  'breakup-editor WP-B'
WHERE NOT EXISTS (
  SELECT 1 FROM kjx_prompt_template
   WHERE work_code = 'breakup-editor' AND kind = 'WORK' AND version = '1.0.0'
);

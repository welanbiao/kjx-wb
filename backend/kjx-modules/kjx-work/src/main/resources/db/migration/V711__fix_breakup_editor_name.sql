-- WP-B1：纠正直播行里被问号替换的作品名。连接串保持 characterEncoding=UTF-8。
UPDATE kjx_work
   SET name = '分手结局修改器',
       summary = '如果那句话没说出口，他们还会分手吗？今天，帮 TA 改写曾经的选择。',
       updater = 'wp-b1'
 WHERE work_code = 'breakup-editor'
   AND deleted = b'0';

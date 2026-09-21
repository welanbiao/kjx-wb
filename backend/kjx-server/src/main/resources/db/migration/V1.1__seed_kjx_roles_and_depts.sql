-- KJX 角色与部门树（在 RuoYi V1 baseline 之后）。
-- HOST = 仅本部门；OPERATOR/PUBLISHER/FINANCE = 全部数据。
-- 不写入业务会员表。

INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag, create_dept, create_by, create_time)
VALUES
  (200, '000000', 0,   '0',           '叩君芯',     0, '0', '0', 200, 1, NOW()),
  (201, '000000', 200, '0,200',       '运营部',     1, '0', '0', 200, 1, NOW()),
  (210, '000000', 200, '0,200',       '主播-A',     10, '0', '0', 200, 1, NOW()),
  (211, '000000', 200, '0,200',       '主播-B',     11, '0', '0', 200, 1, NOW());

INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_dept, create_by, create_time, remark)
VALUES
  (10, '000000', '主播',   'HOST',      10, '3', 1, 1, '0', '0', 200, 1, NOW(), '仅本部门数据权限'),
  (11, '000000', '运营',   'OPERATOR',  11, '1', 1, 1, '0', '0', 200, 1, NOW(), '全部数据权限'),
  (12, '000000', '发行',   'PUBLISHER', 12, '1', 1, 1, '0', '0', 200, 1, NOW(), '作品发行，全部数据'),
  (13, '000000', '财务',   'FINANCE',   13, '1', 1, 1, '0', '0', 200, 1, NOW(), '财务，全部数据');

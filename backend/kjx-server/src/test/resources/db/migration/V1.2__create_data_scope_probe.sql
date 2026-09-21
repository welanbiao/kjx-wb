-- Test-only data-permission fixture. This table must never be packaged in production resources.
CREATE TABLE test_demo
(
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   VARCHAR(20)  NULL DEFAULT '000000' COMMENT '租户编号',
    dept_id     BIGINT       NULL DEFAULT NULL COMMENT '部门id',
    user_id     BIGINT       NULL DEFAULT NULL COMMENT '用户id',
    order_num   INT          NULL DEFAULT 0 COMMENT '排序号',
    test_key    VARCHAR(255) NULL DEFAULT NULL COMMENT 'key键',
    value       VARCHAR(255) NULL DEFAULT NULL COMMENT '值',
    version     INT          NULL DEFAULT 0 COMMENT '版本',
    create_dept BIGINT       NULL DEFAULT NULL COMMENT '创建部门',
    create_time DATETIME(3)  NULL DEFAULT NULL COMMENT '创建时间',
    create_by   BIGINT       NULL DEFAULT NULL COMMENT '创建人',
    update_time DATETIME(3)  NULL DEFAULT NULL COMMENT '更新时间',
    update_by   BIGINT       NULL DEFAULT NULL COMMENT '更新人',
    del_flag    INT          NULL DEFAULT 0 COMMENT '删除标志',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Gate B 测试单表';

-- WP-41 / V625：声明式 UI 节点白名单。非表内 type（除 custom.slot:{name}）一律拒绝。
CREATE TABLE `kjx_ui_node_type` (
  `type`          VARCHAR(64)  NOT NULL,
  `props_schema`  JSON         NOT NULL,
  `enabled`       TINYINT      NOT NULL DEFAULT 1,
  PRIMARY KEY (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='UI 节点白名单';

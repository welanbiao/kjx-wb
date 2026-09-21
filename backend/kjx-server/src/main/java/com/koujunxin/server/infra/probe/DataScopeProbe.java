package com.koujunxin.server.infra.probe;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 映射官方 baseline 的 test_demo，用于 Gate B 数据权限基础设施验证。
 */
@Data
@TableName("test_demo")
public class DataScopeProbe {

    @TableId
    private Long id;
    private Long deptId;
    private Long userId;
    private Integer orderNum;
    private String testKey;
    private String value;
}

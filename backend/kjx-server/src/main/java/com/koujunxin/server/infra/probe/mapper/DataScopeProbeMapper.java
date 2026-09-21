package com.koujunxin.server.infra.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.koujunxin.server.infra.probe.DataScopeProbe;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.annotation.DataColumn;
import org.dromara.common.mybatis.annotation.DataPermission;
import org.springframework.context.annotation.Profile;

/**
 * 列表与按 ID 查询都带 {@code @DataPermission}，配合 {@code DeptDataScopeGuard} 覆盖更新。
 */
@Mapper
@Profile("it")
@DataPermission({
        @DataColumn(key = "deptName", value = "dept_id")
})
public interface DataScopeProbeMapper extends BaseMapper<DataScopeProbe> {
}

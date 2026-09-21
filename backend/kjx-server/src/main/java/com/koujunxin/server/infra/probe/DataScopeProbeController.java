package com.koujunxin.server.infra.probe;

import com.koujunxin.common.permission.DeptDataScopeGuard;
import com.koujunxin.server.infra.probe.mapper.DataScopeProbeMapper;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 基础设施探测接口，不是业务域。用于 Gate A/B 与按 ID 强制校验。
 */
@RestController
@RequestMapping("/admin-api/kjx/infra/probe")
@Profile("it")
public class DataScopeProbeController {

    private final DataScopeProbeMapper mapper;
    private final DeptDataScopeGuard deptDataScopeGuard;

    public DataScopeProbeController(DataScopeProbeMapper mapper, DeptDataScopeGuard deptDataScopeGuard) {
        this.mapper = mapper;
        this.deptDataScopeGuard = deptDataScopeGuard;
    }

    @GetMapping
    public R<List<DataScopeProbe>> list() {
        return R.ok(mapper.selectList(null));
    }

    @GetMapping("/{id}")
    public R<DataScopeProbe> get(@PathVariable Long id) {
        DataScopeProbe row = DataPermissionHelper.ignore(() -> mapper.selectById(id));
        return R.ok(deptDataScopeGuard.requireAccessible(row, row == null ? null : row.getDeptId()));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        DataScopeProbe row = DataPermissionHelper.ignore(() -> mapper.selectById(id));
        deptDataScopeGuard.requireAccessible(row, row == null ? null : row.getDeptId());
        row.setValue(body.getOrDefault("value", row.getValue()));
        mapper.updateById(row);
        return R.ok();
    }
}

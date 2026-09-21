package com.koujunxin.server.infra;

import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.StpKit;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 双域探测：仅用于认证隔离验收，不含业务。
 */
@RestController
@Profile({"test", "it"})
public class DualDomainProbeController {

    @GetMapping("/app/infra/ping")
    public R<Map<String, String>> appPing() {
        return R.ok(Map.of(
                "domain", StpKit.APP_TYPE,
                "loginId", String.valueOf(StpKit.APP.getLoginId())));
    }

    @GetMapping("/admin-api/kjx/infra/ping")
    public R<Map<String, String>> adminPing() {
        return R.ok(Map.of(
                "domain", StpKit.ADMIN_TYPE,
                "loginId", String.valueOf(StpKit.ADMIN.getLoginId())));
    }
}

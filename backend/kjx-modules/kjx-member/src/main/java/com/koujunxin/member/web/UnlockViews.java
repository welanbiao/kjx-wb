package com.koujunxin.member.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.koujunxin.member.domain.UnlockRecordRow;
import com.koujunxin.member.normalize.ExpireTimes;

public final class UnlockViews {

    private UnlockViews() {
    }

    public static Map<String, Object> record(UnlockRecordRow row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.id());
        data.put("deptId", row.deptId());
        data.put("memberId", row.memberId());
        data.put("memberCode", row.memberCode());
        data.put("douyinId", row.douyinId());
        data.put("accountId", row.accountId());
        data.put("workId", row.workId());
        data.put("workName", row.workName());
        data.put("workVersion", row.workVersion());
        data.put("unlockTime", ExpireTimes.format(row.unlockTime()));
        data.put("beforeUnlockedCount", row.beforeUnlockedCount());
        data.put("afterUnlockedCount", row.afterUnlockedCount());
        data.put("beforeRemainCount", row.beforeRemainCount());
        data.put("afterRemainCount", row.afterRemainCount());
        data.put("result", row.result());
        data.put("failReason", row.failReason());
        data.put("source", row.source());
        data.put("operatorId", row.operatorId());
        return data;
    }
}

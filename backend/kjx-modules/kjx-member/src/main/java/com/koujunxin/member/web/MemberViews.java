package com.koujunxin.member.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.koujunxin.member.domain.AppealRecord;
import com.koujunxin.member.domain.BindingRecord;
import com.koujunxin.member.domain.HostRecord;
import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.normalize.ExpireTimes;

public final class MemberViews {

    private MemberViews() {
    }

    public static Map<String, Object> host(HostRecord host, String hostName) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deptId", host.deptId());
        data.put("hostCode", host.hostCode());
        data.put("hostName", hostName);
        data.put("dyAccount", host.dyAccount());
        data.put("logoUrl", host.logoUrl());
        data.put("contact", host.contact());
        data.put("remark", host.remark());
        return data;
    }

    public static Map<String, Object> hostOption(HostRecord host, String hostName) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hostCode", host.hostCode());
        data.put("hostName", hostName);
        return data;
    }

    public static Map<String, Object> member(MemberRecord member, String hostCode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", member.id());
        data.put("memberCode", member.memberCode());
        data.put("douyinId", member.douyinId());
        data.put("deptId", member.deptId());
        data.put("hostCode", hostCode);
        data.put("subscribedMonths", member.subscribedMonths());
        data.put("subscribedDays", member.subscribedDays());
        data.put("expireAt", ExpireTimes.format(member.expireAt()));
        data.put("startAt", ExpireTimes.format(member.startAt()));
        data.put("availableCount", member.availableCount());
        data.put("unlockedCount", member.unlockedCount());
        data.put("remainCount", member.remainCount());
        data.put("status", member.status());
        data.put("bindState", member.bindState());
        data.put("boundAccountId", member.boundAccountId());
        data.put("boundAt", ExpireTimes.format(member.boundAt()));
        data.put("remark", member.remark());
        data.put("createTime", ExpireTimes.format(member.createTime()));
        return data;
    }

    public static Map<String, Object> created(MemberRecord member) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberId", member.id());
        data.put("memberCode", member.memberCode());
        data.put("availableCount", member.availableCount());
        data.put("status", member.status());
        return data;
    }

    public static Map<String, Object> binding(BindingRecord row, String memberCode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.id());
        data.put("deptId", row.deptId());
        data.put("accountId", row.accountId());
        data.put("memberId", row.memberId());
        data.put("memberCode", memberCode);
        data.put("douyinIdInput", row.douyinIdInput());
        data.put("claimMonths", row.claimMonths());
        data.put("status", row.status());
        data.put("rejectReason", row.rejectReason());
        data.put("matchedAt", ExpireTimes.format(row.matchedAt()));
        data.put("createTime", ExpireTimes.format(row.createTime()));
        return data;
    }

    public static Map<String, Object> appeal(AppealRecord row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.id());
        data.put("accountId", row.accountId());
        data.put("bindingId", row.bindingId());
        data.put("appealType", row.appealType());
        data.put("content", row.content());
        data.put("contact", row.contact());
        data.put("status", row.status());
        data.put("handleNote", row.handleNote());
        data.put("createTime", ExpireTimes.format(row.createTime()));
        return data;
    }

    public static Map<String, Object> page(List<?> list, long total) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    public static String hostCodeOf(MemberRecord member) {
        String code = member.memberCode();
        int dash = code.indexOf('-');
        return dash > 0 ? code.substring(0, dash) : code;
    }
}

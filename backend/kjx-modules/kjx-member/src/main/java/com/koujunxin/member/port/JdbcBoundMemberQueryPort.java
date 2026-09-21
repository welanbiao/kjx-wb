package com.koujunxin.member.port;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.koujunxin.account.api.port.BoundMemberQueryPort;
import com.koujunxin.member.domain.BindingRecord;
import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.jdbc.BindingRepository;
import com.koujunxin.member.jdbc.MemberRepository;
import com.koujunxin.member.normalize.ExpireTimes;
import com.koujunxin.member.recalc.MemberRecalcService;

/**
 * WP-10 {@code BoundMemberQueryPort} 实现。仅 MATCHED 返回快照。
 */
public final class JdbcBoundMemberQueryPort implements BoundMemberQueryPort {

    private final BindingRepository bindings;
    private final MemberRepository members;
    private final MemberRecalcService recalc;

    public JdbcBoundMemberQueryPort(BindingRepository bindings, MemberRepository members, MemberRecalcService recalc) {
        this.bindings = bindings;
        this.members = members;
        this.recalc = recalc;
    }

    @Override
    public Optional<Object> findSnapshot(long accountId) {
        Optional<BindingRecord> binding = bindings.findByAccountId(accountId);
        if (binding.isEmpty() || !BindingRecord.MATCHED.equals(binding.get().status())) {
            return Optional.empty();
        }
        return members.findById(binding.get().memberId()).map(this::snapshot);
    }

    public Map<String, Object> snapshot(MemberRecord member) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberCode", member.memberCode());
        data.put("douyinId", member.douyinId());
        data.put("expireAt", ExpireTimes.format(member.expireAt()));
        data.put("startAt", ExpireTimes.format(member.startAt()));
        data.put("availableCount", member.availableCount());
        data.put("unlockedCount", member.unlockedCount());
        data.put("remainCount", member.remainCount());
        data.put("status", member.status());
        data.put("subscribedMonths", member.subscribedMonths());
        data.put("subscribedDays", member.subscribedDays());
        return data;
    }

    public MemberRecalcService recalc() {
        return recalc;
    }
}

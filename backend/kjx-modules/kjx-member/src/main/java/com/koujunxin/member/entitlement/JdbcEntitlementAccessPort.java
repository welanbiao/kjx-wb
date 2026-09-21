package com.koujunxin.member.entitlement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.koujunxin.member.api.port.MemberAuditPort;
import com.koujunxin.member.domain.EntitlementRecord;
import com.koujunxin.member.jdbc.EntitlementRepository;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.work.api.WorkCatalogQueryPort;
import com.koujunxin.work.api.WorkSnapshot;

/**
 * Runtime/Tool 访问只认 entitlement 事实，不复用 {@code NewUnlockPolicy}。
 */
public class JdbcEntitlementAccessPort implements EntitlementAccessPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcEntitlementAccessPort.class);

    private final EntitlementRepository entitlements;
    private final WorkCatalogQueryPort works;
    private final MemberAuditPort audit;

    public JdbcEntitlementAccessPort(
            EntitlementRepository entitlements,
            WorkCatalogQueryPort works,
            MemberAuditPort audit) {
        this.entitlements = entitlements;
        this.works = works;
        this.audit = audit;
    }

    @Override
    public void requireEntitlement(long accountId, String workCode) {
        WorkSnapshot work = works.findByCode(workCode).orElse(null);
        if (work == null) {
            throw new EntitlementRequiredException(accountId, workCode);
        }
        EntitlementRecord row = entitlements.findAccessible(accountId, work.id()).orElse(null);
        if (row == null) {
            throw new EntitlementRequiredException(accountId, workCode);
        }
        if (row.accountId() != null && row.accountId() != accountId) {
            log.warn("entitlement account mismatch accountId={} workId={} entitlementId={}",
                    accountId, work.id(), row.id());
            audit.record("entitlement", row.id(), row.deptId(), "SEC_MISMATCH", "account mismatch", accountId);
            throw new EntitlementRequiredException(accountId, workCode);
        }
    }
}

import { toAdjustEntitlementBody } from "./entitlement-adapter";
import { apiDownload, apiFetch, newIdempotencyKey } from "../lib/api";
import type {
  AdminAppeal,
  AdminBinding,
  AdminHost,
  AdminMember,
  AdminWork,
  BindingPageQuery,
  CreateHostMemberData,
  CreateHostMemberRequest,
  CreateHostRequest,
  CreateOpsMemberRequest,
  CreateWorkRequest,
  HandleAppealRequest,
  HostSelfInfo,
  MemberPageQuery,
  PageResult,
  RecalcMemberRequest,
  ReviewReasonRequest,
  UnlockRecord,
  UnlockRecordQuery,
  UpdateHostMemberRequest,
  UpdateHostRequest,
  UpdateWorkRequest,
} from "../lib/types";

function qs(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value == null || value === "") {
      continue;
    }
    search.set(key, String(value));
  }
  const encoded = search.toString();
  return encoded ? `?${encoded}` : "";
}

function writeInit(body: unknown): RequestInit & { idempotencyKey: string } {
  return {
    method: "POST",
    idempotencyKey: newIdempotencyKey(),
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

export const selfApi = {
  host() {
    return apiFetch<HostSelfInfo>("/admin-api/kjx/self/host");
  },
  pageMembers(query: MemberPageQuery) {
    return apiFetch<PageResult<AdminMember>>(
      `/admin-api/kjx/self/member/page${qs({
        douyinId: query.douyinId,
        memberCode: query.memberCode,
        status: query.status,
        bindState: query.bindState,
        pageNo: query.pageNo,
        pageSize: query.pageSize,
      })}`,
    );
  },
  createMember(body: CreateHostMemberRequest) {
    return apiFetch<CreateHostMemberData>("/admin-api/kjx/self/member", writeInit(body));
  },
  updateMember(id: number, body: UpdateHostMemberRequest) {
    return apiFetch<AdminMember>(`/admin-api/kjx/self/member/${id}`, {
      ...writeInit(body),
      method: "PUT",
    });
  },
  memberCode(id: number) {
    return apiFetch<{ memberCode: string }>(`/admin-api/kjx/self/member/${id}/code`);
  },
  pageBindings(query: BindingPageQuery) {
    return apiFetch<PageResult<AdminBinding>>(
      `/admin-api/kjx/self/binding/page${qs({
        status: query.status,
        memberCode: query.memberCode,
        pageNo: query.pageNo,
        pageSize: query.pageSize,
      })}`,
    );
  },
  exportMembers(status?: string) {
    return apiDownload(`/admin-api/kjx/self/member/export${qs({ status })}`);
  },
};

export const opsApi = {
  pageMembers(query: MemberPageQuery) {
    return apiFetch<PageResult<AdminMember>>(
      `/admin-api/kjx/member/page${qs({
        deptId: query.deptId,
        douyinId: query.douyinId,
        memberCode: query.memberCode,
        status: query.status,
        bindState: query.bindState,
        pageNo: query.pageNo,
        pageSize: query.pageSize,
      })}`,
    );
  },
  createMember(body: CreateOpsMemberRequest) {
    return apiFetch<CreateHostMemberData>("/admin-api/kjx/member", writeInit(body));
  },
  getMember(id: number) {
    return apiFetch<AdminMember>(`/admin-api/kjx/member/${id}`);
  },
  updateMember(id: number, body: UpdateHostMemberRequest) {
    return apiFetch<AdminMember>(`/admin-api/kjx/member/${id}`, {
      ...writeInit(body),
      method: "PUT",
    });
  },
  recalcMember(id: number, body: RecalcMemberRequest) {
    return apiFetch<AdminMember>(`/admin-api/kjx/member/${id}/recalc`, writeInit(body));
  },
  adjustEntitlement(id: number, input: { action: "ADD" | "REMOVE"; workCode: string; reason: string }) {
    return apiFetch<AdminMember>(
      `/admin-api/kjx/member/${id}/entitlement`,
      writeInit(toAdjustEntitlementBody(input)),
    );
  },
  pageBindings(query: BindingPageQuery) {
    return apiFetch<PageResult<AdminBinding>>(
      `/admin-api/kjx/binding/page${qs({
        status: query.status,
        deptId: query.deptId,
        memberCode: query.memberCode,
        pageNo: query.pageNo,
        pageSize: query.pageSize,
      })}`,
    );
  },
  approveBinding(id: number, remark?: string) {
    return apiFetch(`/admin-api/kjx/binding/${id}/approve`, writeInit(remark ? { remark } : {}));
  },
  rejectBinding(id: number, body: ReviewReasonRequest) {
    return apiFetch(`/admin-api/kjx/binding/${id}/reject`, writeInit(body));
  },
  forceUnbind(id: number, body: ReviewReasonRequest) {
    return apiFetch(`/admin-api/kjx/binding/${id}/force-unbind`, writeInit(body));
  },
  pageAppeals(query: { status?: string; pageNo?: number; pageSize?: number }) {
    return apiFetch<PageResult<AdminAppeal>>(
      `/admin-api/kjx/appeal/page${qs({
        status: query.status,
        pageNo: query.pageNo,
        pageSize: query.pageSize,
      })}`,
    );
  },
  handleAppeal(id: number, body: HandleAppealRequest) {
    return apiFetch(`/admin-api/kjx/appeal/${id}/handle`, writeInit(body));
  },
  pageUnlocks(query: UnlockRecordQuery) {
    return apiFetch<PageResult<UnlockRecord>>(
      `/admin-api/kjx/unlock-record/page${qs({
        deptId: query.deptId,
        memberId: query.memberId,
        workCode: query.workCode,
        result: query.result,
        source: query.source,
        pageNo: query.pageNo,
        pageSize: query.pageSize,
      })}`,
    );
  },
  exportUnlocks(query: UnlockRecordQuery) {
    return apiDownload(
      `/admin-api/kjx/unlock-record/export${qs({
        deptId: query.deptId,
        memberId: query.memberId,
        result: query.result,
        source: query.source,
      })}`,
    );
  },
  pageWorks(query: { workType?: string; status?: string; keyword?: string; pageNo?: number; pageSize?: number }) {
    return apiFetch<PageResult<AdminWork>>(
      `/admin-api/kjx/work/page${qs({
        workType: query.workType,
        status: query.status,
        keyword: query.keyword,
        pageNo: query.pageNo,
        pageSize: query.pageSize,
      })}`,
    );
  },
  createWork(body: CreateWorkRequest) {
    return apiFetch<AdminWork>("/admin-api/kjx/work", writeInit(body));
  },
  getWork(id: number) {
    return apiFetch<AdminWork>(`/admin-api/kjx/work/${id}`);
  },
  updateWork(id: number, body: UpdateWorkRequest) {
    return apiFetch<AdminWork>(`/admin-api/kjx/work/${id}`, {
      ...writeInit(body),
      method: "PUT",
    });
  },
  pageHosts(query: { hostCode?: string; pageNo?: number; pageSize?: number }) {
    return apiFetch<PageResult<AdminHost>>(
      `/admin-api/kjx/host/page${qs({
        hostCode: query.hostCode,
        pageNo: query.pageNo,
        pageSize: query.pageSize,
      })}`,
    );
  },
  createHost(body: CreateHostRequest) {
    return apiFetch<AdminHost>("/admin-api/kjx/host", writeInit(body));
  },
  getHost(id: number) {
    return apiFetch<AdminHost>(`/admin-api/kjx/host/${id}`);
  },
  updateHost(id: number, body: UpdateHostRequest) {
    return apiFetch<AdminHost>(`/admin-api/kjx/host/${id}`, {
      ...writeInit(body),
      method: "PUT",
    });
  },
};

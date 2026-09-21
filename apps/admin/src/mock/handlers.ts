import { toAdjustEntitlementBody } from "../api/entitlement-adapter";
import { ApiError } from "../lib/errors";
import { canTransit } from "../lib/work-status";
import type {
  AdminMember,
  AdminWork,
  ApiEnvelope,
  BindingPageQuery,
  CreateHostMemberRequest,
  CreateHostRequest,
  CreateWorkRequest,
  MemberPageQuery,
  UnlockRecordQuery,
  UpdateHostMemberRequest,
  UpdateHostRequest,
  UpdateWorkRequest,
  WorkStatus,
} from "../lib/types";
import {
  asHostSelf,
  createdView,
  floorAvailable,
  getMockStore,
  isHostSession,
  isOpsSession,
  loginMock,
  memberStatus,
  randomMemberSuffix,
  sessionFromToken,
  type MockState,
} from "./store";

type HandlerResult =
  | { kind: "json"; envelope: ApiEnvelope<unknown>; httpStatus: number }
  | { kind: "blob"; blob: Blob; requestId: string };

function envelope<T>(requestId: string, data: T, code = 0, msg = ""): ApiEnvelope<T> {
  return { code, data, msg, requestId };
}

function fail(requestId: string, httpStatus: number, code: number, msg: string): HandlerResult {
  return { kind: "json", httpStatus, envelope: envelope(requestId, null, code, msg) };
}

function ok<T>(requestId: string, data: T, httpStatus = 200): HandlerResult {
  return { kind: "json", httpStatus, envelope: envelope(requestId, data) };
}

function jsonBody(init?: RequestInit): Record<string, unknown> {
  if (!init?.body) {
    return {};
  }
  if (typeof init.body === "string") {
    try {
      return JSON.parse(init.body) as Record<string, unknown>;
    } catch {
      return {};
    }
  }
  return {};
}

function query(path: string): URLSearchParams {
  const q = path.includes("?") ? path.slice(path.indexOf("?")) : "";
  return new URLSearchParams(q);
}

function pathname(path: string): string {
  return path.split("?")[0] ?? path;
}

function header(init: RequestInit | undefined, name: string): string | null {
  const headers = new Headers(init?.headers);
  return headers.get(name);
}

function requireSession(init: RequestInit | undefined, requestId: string) {
  const auth = header(init, "Authorization");
  const token = auth?.startsWith("Bearer ") ? auth.slice(7) : null;
  const session = sessionFromToken(token);
  if (!session) {
    throw new ApiError({ code: 401, msg: "未登录或会话已失效", requestId, httpStatus: 401 });
  }
  return session;
}

function requireHost(init: RequestInit | undefined, requestId: string) {
  const session = requireSession(init, requestId);
  if (!isHostSession(session)) {
    throw new ApiError({ code: 403, msg: "无权限", requestId, httpStatus: 403 });
  }
  return session;
}

function requireOps(init: RequestInit | undefined, requestId: string) {
  const session = requireSession(init, requestId);
  if (!isOpsSession(session)) {
    throw new ApiError({ code: 403, msg: "无权限", requestId, httpStatus: 403 });
  }
  return session;
}

function pageOf<T>(list: T[], pageNo: number, pageSize: number) {
  const start = (pageNo - 1) * pageSize;
  return { list: list.slice(start, start + pageSize), total: list.length };
}

function num(value: string | null): number | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function filterMembers(store: MockState, q: MemberPageQuery, deptLock?: number): AdminMember[] {
  return store.members.filter((item) => {
    if (deptLock != null && item.deptId !== deptLock) {
      return false;
    }
    if (q.deptId != null && item.deptId !== q.deptId) {
      return false;
    }
    if (q.douyinId && !item.douyinId.includes(q.douyinId)) {
      return false;
    }
    if (q.memberCode && !item.memberCode.includes(q.memberCode)) {
      return false;
    }
    if (q.status && item.status !== q.status) {
      return false;
    }
    if (q.bindState != null && item.bindState !== q.bindState) {
      return false;
    }
    return true;
  });
}

function parseMemberCreate(body: Record<string, unknown>): CreateHostMemberRequest {
  const douyinId = String(body.douyinId ?? "").trim();
  const subscribedMonths = body.subscribedMonths;
  const subscribedDays = body.subscribedDays;
  const expireAt = String(body.expireAt ?? "").trim();
  if (!douyinId || subscribedMonths == null || subscribedDays == null || !expireAt) {
    throw new Error("月份、天数、到期时间均必填");
  }
  if (typeof subscribedMonths !== "number" || typeof subscribedDays !== "number") {
    throw new Error("月份、天数必须为数字且互不换算");
  }
  return {
    douyinId,
    subscribedMonths,
    subscribedDays,
    expireAt,
    remark: String(body.remark ?? ""),
  };
}

function applyCreate(store: MockState, deptId: number, payload: CreateHostMemberRequest): AdminMember {
  const host = store.hosts.find((item) => item.deptId === deptId);
  const hostCode = host?.hostCode ?? "KJX";
  const member: AdminMember = {
    id: store.nextMemberId++,
    memberCode: `${hostCode}-${randomMemberSuffix()}`,
    douyinId: payload.douyinId,
    deptId,
    hostCode,
    subscribedMonths: payload.subscribedMonths,
    subscribedDays: payload.subscribedDays,
    expireAt: payload.expireAt,
    startAt: "2026-09-22T00:00:00.000+08:00",
    availableCount: floorAvailable(payload.subscribedDays),
    unlockedCount: 0,
    remainCount: floorAvailable(payload.subscribedDays),
    status: memberStatus(payload.expireAt, "2026-09-22T00:00:00.000+08:00"),
    bindState: 0,
    boundAccountId: null,
    boundAt: null,
    remark: payload.remark ?? "",
    createTime: "2026-09-22T12:00:00.000+08:00",
  };
  store.members.unshift(member);
  return member;
}

function applyUpdate(member: AdminMember, body: UpdateHostMemberRequest): AdminMember {
  if (!body.reason?.trim()) {
    throw new Error("必须填写操作理由");
  }
  const next = { ...member };
  if (body.subscribedMonths != null) {
    next.subscribedMonths = body.subscribedMonths;
  }
  if (body.subscribedDays != null) {
    next.subscribedDays = body.subscribedDays;
    next.availableCount = floorAvailable(body.subscribedDays);
    next.remainCount = Math.max(0, next.availableCount - next.unlockedCount);
  }
  if (body.expireAt) {
    next.expireAt = body.expireAt;
  }
  if (body.remark != null) {
    next.remark = body.remark;
  }
  next.status = memberStatus(next.expireAt, next.startAt);
  return next;
}

function xlsxStub(rows: AdminMember[]): Blob {
  const header = "memberCode,douyinId,deptId,subscribedMonths,subscribedDays,expireAt";
  const lines = rows.map(
    (row) =>
      `${row.memberCode},${row.douyinId},${row.deptId},${row.subscribedMonths},${row.subscribedDays},${row.expireAt}`,
  );
  return new Blob([[header, ...lines].join("\n")], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
}

export async function dispatchMock(path: string, init?: RequestInit): Promise<HandlerResult> {
  const requestId = header(init, "X-Request-Id") ?? crypto.randomUUID();
  const method = (init?.method ?? "GET").toUpperCase();
  const url = pathname(path);
  const q = query(path);
  const store = getMockStore();
  const body = jsonBody(init);

  try {
    if (url === "/admin-api/auth/login" && method === "POST") {
      try {
        const session = loginMock(String(body.username ?? ""), String(body.password ?? ""));
        return ok(requestId, session);
      } catch (error) {
        return fail(requestId, 401, 401, error instanceof Error ? error.message : "登录失败");
      }
    }
    if (url === "/admin-api/auth/logout" && method === "POST") {
      return ok(requestId, {});
    }
    if (url === "/admin-api/system/user/getInfo" && method === "GET") {
      const session = requireSession(init, requestId);
      return ok(requestId, session);
    }

    if (url === "/admin-api/kjx/self/host" && method === "GET") {
      const session = requireHost(init, requestId);
      const host = store.hosts.find((item) => item.deptId === session.deptId);
      if (!host) {
        return fail(requestId, 404, 404, "主播资料不存在");
      }
      return ok(requestId, asHostSelf(host));
    }

    if (url === "/admin-api/kjx/self/member/page" && method === "GET") {
      const session = requireHost(init, requestId);
      const pageNo = num(q.get("pageNo")) ?? 1;
      const pageSize = num(q.get("pageSize")) ?? 20;
      const list = filterMembers(
        store,
        {
          douyinId: q.get("douyinId") ?? undefined,
          memberCode: q.get("memberCode") ?? undefined,
          status: (q.get("status") as MemberPageQuery["status"]) ?? undefined,
          bindState: q.get("bindState") == null ? undefined : (Number(q.get("bindState")) as 0 | 1),
        },
        session.deptId ?? -1,
      );
      return ok(requestId, pageOf(list, pageNo, pageSize));
    }

    if (url === "/admin-api/kjx/self/member" && method === "POST") {
      const session = requireHost(init, requestId);
      const created = applyCreate(store, session.deptId ?? -1, parseMemberCreate(body));
      return ok(requestId, createdView(created));
    }

    const selfMember = url.match(/^\/admin-api\/kjx\/self\/member\/(\d+)$/);
    if (selfMember && method === "PUT") {
      const session = requireHost(init, requestId);
      const id = Number(selfMember[1]);
      const index = store.members.findIndex((item) => item.id === id);
      if (index < 0 || store.members[index].deptId !== session.deptId) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      store.members[index] = applyUpdate(store.members[index], body as UpdateHostMemberRequest);
      return ok(requestId, store.members[index]);
    }

    const selfCode = url.match(/^\/admin-api\/kjx\/self\/member\/(\d+)\/code$/);
    if (selfCode && method === "GET") {
      const session = requireHost(init, requestId);
      const id = Number(selfCode[1]);
      const member = store.members.find((item) => item.id === id && item.deptId === session.deptId);
      if (!member) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      return ok(requestId, { memberCode: member.memberCode });
    }

    if (url === "/admin-api/kjx/self/binding/page" && method === "GET") {
      const session = requireHost(init, requestId);
      const pageNo = num(q.get("pageNo")) ?? 1;
      const pageSize = num(q.get("pageSize")) ?? 20;
      const status = q.get("status");
      const memberCode = q.get("memberCode");
      const list = store.bindings.filter(
        (item) =>
          item.deptId === session.deptId &&
          (!status || item.status === status) &&
          (!memberCode || (item.memberCode ?? "").includes(memberCode)),
      );
      return ok(requestId, pageOf(list, pageNo, pageSize));
    }

    if (url === "/admin-api/kjx/self/member/export" && method === "GET") {
      const session = requireHost(init, requestId);
      const status = q.get("status") as MemberPageQuery["status"];
      const rows = store.members.filter(
        (item) => item.deptId === session.deptId && (!status || item.status === status),
      );
      return { kind: "blob", blob: xlsxStub(rows), requestId };
    }

    if (url === "/admin-api/kjx/member/page" && method === "GET") {
      requireOps(init, requestId);
      const pageNo = num(q.get("pageNo")) ?? 1;
      const pageSize = num(q.get("pageSize")) ?? 20;
      const list = filterMembers(store, {
        deptId: num(q.get("deptId")),
        douyinId: q.get("douyinId") ?? undefined,
        memberCode: q.get("memberCode") ?? undefined,
        status: (q.get("status") as MemberPageQuery["status"]) ?? undefined,
        bindState: q.get("bindState") == null ? undefined : (Number(q.get("bindState")) as 0 | 1),
      });
      return ok(requestId, pageOf(list, pageNo, pageSize));
    }

    if (url === "/admin-api/kjx/member" && method === "POST") {
      requireOps(init, requestId);
      if (!String(body.reason ?? "").trim()) {
        return fail(requestId, 400, 400, "必须填写操作理由");
      }
      const deptId = Number(body.deptId);
      if (!deptId) {
        return fail(requestId, 400, 400, "deptId 必填");
      }
      const created = applyCreate(store, deptId, parseMemberCreate(body));
      return ok(requestId, createdView(created));
    }

    const memberId = url.match(/^\/admin-api\/kjx\/member\/(\d+)$/);
    if (memberId && method === "GET") {
      requireOps(init, requestId);
      const found = store.members.find((item) => item.id === Number(memberId[1]));
      if (!found) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      return ok(requestId, found);
    }
    if (memberId && method === "PUT") {
      requireOps(init, requestId);
      const index = store.members.findIndex((item) => item.id === Number(memberId[1]));
      if (index < 0) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      store.members[index] = applyUpdate(store.members[index], body as UpdateHostMemberRequest);
      return ok(requestId, store.members[index]);
    }

    const recalc = url.match(/^\/admin-api\/kjx\/member\/(\d+)\/recalc$/);
    if (recalc && method === "POST") {
      requireOps(init, requestId);
      if (!String(body.reason ?? "").trim()) {
        return fail(requestId, 400, 400, "必须填写操作理由");
      }
      const index = store.members.findIndex((item) => item.id === Number(recalc[1]));
      if (index < 0) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      const current = store.members[index];
      store.members[index] = {
        ...current,
        availableCount: floorAvailable(current.subscribedDays),
        remainCount: Math.max(0, floorAvailable(current.subscribedDays) - current.unlockedCount),
        status: memberStatus(current.expireAt, current.startAt),
      };
      return ok(requestId, store.members[index]);
    }

    const entitlement = url.match(/^\/admin-api\/kjx\/member\/(\d+)\/entitlement$/);
    if (entitlement && method === "POST") {
      requireOps(init, requestId);
      const payload = toAdjustEntitlementBody({
        action: body.action as "ADD" | "REMOVE",
        workCode: String(body.workCode ?? ""),
        reason: String(body.reason ?? ""),
      });
      const index = store.members.findIndex((item) => item.id === Number(entitlement[1]));
      if (index < 0) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      const member = store.members[index];
      if (payload.action === "ADD") {
        store.entitlements.push({ memberId: member.id, workCode: payload.workCode, source: "official_add" });
        member.unlockedCount += 1;
        member.remainCount = Math.max(0, member.availableCount - member.unlockedCount);
        store.unlocks.unshift({
          id: store.nextUnlockId++,
          deptId: member.deptId,
          memberId: member.id,
          memberCode: member.memberCode,
          douyinId: member.douyinId,
          workCode: payload.workCode,
          unlockTime: "2026-09-22T12:30:00.000+08:00",
          result: "SUCCESS",
          source: "official_add",
          operatorId: 301,
        });
      } else {
        store.entitlements = store.entitlements.filter(
          (item) => !(item.memberId === member.id && item.workCode === payload.workCode),
        );
        member.unlockedCount = Math.max(0, member.unlockedCount - 1);
        member.remainCount = Math.max(0, member.availableCount - member.unlockedCount);
        store.unlocks.unshift({
          id: store.nextUnlockId++,
          deptId: member.deptId,
          memberId: member.id,
          memberCode: member.memberCode,
          douyinId: member.douyinId,
          workCode: payload.workCode,
          unlockTime: "2026-09-22T12:31:00.000+08:00",
          result: "SUCCESS",
          source: "official_remove",
          operatorId: 301,
        });
      }
      store.members[index] = member;
      return ok(requestId, member);
    }

    if (url === "/admin-api/kjx/binding/page" && method === "GET") {
      requireOps(init, requestId);
      const pageNo = num(q.get("pageNo")) ?? 1;
      const pageSize = num(q.get("pageSize")) ?? 20;
      const queryBody: BindingPageQuery = {
        status: (q.get("status") as BindingPageQuery["status"]) ?? undefined,
        deptId: num(q.get("deptId")),
        memberCode: q.get("memberCode") ?? undefined,
      };
      const list = store.bindings.filter(
        (item) =>
          (!queryBody.status || item.status === queryBody.status) &&
          (queryBody.deptId == null || item.deptId === queryBody.deptId) &&
          (!queryBody.memberCode || (item.memberCode ?? "").includes(queryBody.memberCode)),
      );
      return ok(requestId, pageOf(list, pageNo, pageSize));
    }

    const approve = url.match(/^\/admin-api\/kjx\/binding\/(\d+)\/approve$/);
    if (approve && method === "POST") {
      requireOps(init, requestId);
      const index = store.bindings.findIndex((item) => item.id === Number(approve[1]));
      if (index < 0) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      store.bindings[index] = {
        ...store.bindings[index],
        status: "MATCHED",
        matchedAt: "2026-09-22T12:00:00.000+08:00",
        rejectReason: null,
      };
      const member = store.members.find((item) => item.id === store.bindings[index].memberId);
      if (member) {
        member.bindState = 1;
        member.boundAccountId = store.bindings[index].accountId;
        member.boundAt = store.bindings[index].matchedAt;
      }
      return ok(requestId, {});
    }

    const reject = url.match(/^\/admin-api\/kjx\/binding\/(\d+)\/reject$/);
    if (reject && method === "POST") {
      requireOps(init, requestId);
      if (!String(body.reason ?? "").trim()) {
        return fail(requestId, 400, 400, "必须填写操作理由");
      }
      const index = store.bindings.findIndex((item) => item.id === Number(reject[1]));
      if (index < 0) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      store.bindings[index] = {
        ...store.bindings[index],
        status: "REJECTED",
        rejectReason: String(body.reason),
      };
      return ok(requestId, {});
    }

    const unbind = url.match(/^\/admin-api\/kjx\/binding\/(\d+)\/force-unbind$/);
    if (unbind && method === "POST") {
      requireOps(init, requestId);
      if (!String(body.reason ?? "").trim()) {
        return fail(requestId, 400, 400, "必须填写操作理由");
      }
      const index = store.bindings.findIndex((item) => item.id === Number(unbind[1]));
      if (index < 0) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      store.bindings[index] = { ...store.bindings[index], status: "UNBINDING" };
      const member = store.members.find((item) => item.id === store.bindings[index].memberId);
      if (member) {
        member.bindState = 0;
        member.boundAccountId = null;
        member.boundAt = null;
      }
      return ok(requestId, {});
    }

    if (url === "/admin-api/kjx/appeal/page" && method === "GET") {
      requireOps(init, requestId);
      const pageNo = num(q.get("pageNo")) ?? 1;
      const pageSize = num(q.get("pageSize")) ?? 20;
      const status = q.get("status");
      const list = store.appeals.filter((item) => !status || item.status === status);
      return ok(requestId, pageOf(list, pageNo, pageSize));
    }

    const handle = url.match(/^\/admin-api\/kjx\/appeal\/(\d+)\/handle$/);
    if (handle && method === "POST") {
      requireOps(init, requestId);
      if (!String(body.handleNote ?? "").trim() || !body.result) {
        return fail(requestId, 400, 400, "处理结论与备注必填");
      }
      const index = store.appeals.findIndex((item) => item.id === Number(handle[1]));
      if (index < 0) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      store.appeals[index] = {
        ...store.appeals[index],
        status: body.result === "APPROVED" ? "APPROVED" : "REJECTED",
        handleNote: String(body.handleNote),
      };
      return ok(requestId, {});
    }

    if (url === "/admin-api/kjx/unlock-record/page" && method === "GET") {
      const session = requireSession(init, requestId);
      const filter: UnlockRecordQuery = {
        deptId: num(q.get("deptId")),
        memberId: num(q.get("memberId")),
        workCode: q.get("workCode") ?? undefined,
        result: (q.get("result") as UnlockRecordQuery["result"]) ?? undefined,
        source: (q.get("source") as UnlockRecordQuery["source"]) ?? undefined,
      };
      let list = store.unlocks.filter((item) => {
        if (filter.deptId != null && item.deptId !== filter.deptId) {
          return false;
        }
        if (filter.memberId != null && item.memberId !== filter.memberId) {
          return false;
        }
        if (filter.workCode && item.workCode !== filter.workCode) {
          return false;
        }
        if (filter.result && item.result !== filter.result) {
          return false;
        }
        if (filter.source && item.source !== filter.source) {
          return false;
        }
        return true;
      });
      if (isHostSession(session)) {
        list = list.filter((item) => item.deptId === session.deptId);
      }
      const pageNo = num(q.get("pageNo")) ?? 1;
      const pageSize = num(q.get("pageSize")) ?? 20;
      return ok(requestId, pageOf(list, pageNo, pageSize));
    }

    if (url === "/admin-api/kjx/unlock-record/export" && method === "GET") {
      requireOps(init, requestId);
      const filterDept = num(q.get("deptId"));
      const filterMember = num(q.get("memberId"));
      const result = q.get("result");
      const source = q.get("source");
      const rows = store.unlocks.filter((item) => {
        if (filterDept != null && item.deptId !== filterDept) {
          return false;
        }
        if (filterMember != null && item.memberId !== filterMember) {
          return false;
        }
        if (result && item.result !== result) {
          return false;
        }
        if (source && item.source !== source) {
          return false;
        }
        return true;
      });
      const header = "id,deptId,memberId,workCode,result,source,unlockTime";
      const lines = rows.map(
        (row) =>
          `${row.id},${row.deptId},${row.memberId},${row.workCode},${row.result},${row.source},${row.unlockTime}`,
      );
      return {
        kind: "blob",
        blob: new Blob([[header, ...lines].join("\n")], {
          type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        }),
        requestId,
      };
    }

    if (url === "/admin-api/kjx/work/page" && method === "GET") {
      requireOps(init, requestId);
      const pageNo = num(q.get("pageNo")) ?? 1;
      const pageSize = num(q.get("pageSize")) ?? 20;
      const workType = q.get("workType");
      const status = q.get("status");
      const keyword = q.get("keyword")?.trim();
      const list = store.works.filter(
        (item) =>
          (!workType || item.workType === workType) &&
          (!status || item.status === status) &&
          (!keyword || item.name.includes(keyword) || item.workCode.includes(keyword)),
      );
      return ok(requestId, pageOf(list, pageNo, pageSize));
    }

    if (url === "/admin-api/kjx/work" && method === "POST") {
      requireOps(init, requestId);
      const payload = body as unknown as CreateWorkRequest;
      if (!payload.reason?.trim()) {
        return fail(requestId, 400, 400, "必须填写操作理由");
      }
      const work: AdminWork = {
        id: store.nextWorkId++,
        workCode: payload.workCode,
        name: payload.name,
        workType: payload.workType,
        summary: payload.summary ?? null,
        coverUrl: payload.coverUrl ?? null,
        category: payload.category ?? null,
        tags: payload.tags ?? [],
        status: "DRAFT",
        currentVersion: null,
        contentVersion: null,
        accessForm: payload.accessForm ?? "A",
        grayRatio: payload.grayRatio ?? 0,
        sortWeight: payload.sortWeight ?? 0,
        publishTime: null,
        createTime: "2026-09-22T12:00:00.000+08:00",
      };
      store.works.unshift(work);
      return ok(requestId, work);
    }

    const workId = url.match(/^\/admin-api\/kjx\/work\/(\d+)$/);
    if (workId && method === "GET") {
      requireOps(init, requestId);
      const found = store.works.find((item) => item.id === Number(workId[1]));
      if (!found) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      return ok(requestId, found);
    }
    if (workId && method === "PUT") {
      requireOps(init, requestId);
      const payload = body as unknown as UpdateWorkRequest;
      if (!payload.reason?.trim()) {
        return fail(requestId, 400, 400, "必须填写操作理由");
      }
      const index = store.works.findIndex((item) => item.id === Number(workId[1]));
      if (index < 0) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      const current = store.works[index];
      if (payload.status && !canTransit(current.status, payload.status as WorkStatus)) {
        return fail(requestId, 409, 409, `不能从 ${current.status} 转到 ${payload.status}`);
      }
      store.works[index] = {
        ...current,
        name: payload.name ?? current.name,
        summary: payload.summary ?? current.summary,
        coverUrl: payload.coverUrl ?? current.coverUrl,
        category: payload.category ?? current.category,
        tags: payload.tags ?? current.tags,
        status: (payload.status as WorkStatus) ?? current.status,
        accessForm: payload.accessForm ?? current.accessForm,
        grayRatio: payload.grayRatio ?? current.grayRatio,
        sortWeight: payload.sortWeight ?? current.sortWeight,
        publishTime:
          payload.status === "PUBLISHED" || payload.status === "GRAY"
            ? current.publishTime ?? "2026-09-22T12:00:00.000+08:00"
            : current.publishTime,
      };
      return ok(requestId, store.works[index]);
    }

    if (url === "/admin-api/kjx/host/page" && method === "GET") {
      requireOps(init, requestId);
      const pageNo = num(q.get("pageNo")) ?? 1;
      const pageSize = num(q.get("pageSize")) ?? 20;
      const hostCode = q.get("hostCode");
      const list = store.hosts.filter((item) => !hostCode || item.hostCode.includes(hostCode));
      return ok(requestId, pageOf(list, pageNo, pageSize));
    }

    if (url === "/admin-api/kjx/host" && method === "POST") {
      requireOps(init, requestId);
      const payload = body as unknown as CreateHostRequest;
      if (!payload.reason?.trim() || !payload.adminUserId || !payload.hostCode || !payload.hostName) {
        return fail(requestId, 400, 400, "hostCode、hostName、adminUserId、reason 必填");
      }
      const host = {
        deptId: store.nextHostDeptId++,
        hostCode: payload.hostCode,
        hostName: payload.hostName,
        dyAccount: payload.dyAccount ?? null,
        logoUrl: payload.logoUrl ?? null,
        contact: payload.contact ?? null,
        remark: payload.remark ?? `adminUserId=${payload.adminUserId}`,
      };
      store.hosts.unshift(host);
      return ok(requestId, host);
    }

    const hostId = url.match(/^\/admin-api\/kjx\/host\/(\d+)$/);
    if (hostId && method === "GET") {
      requireOps(init, requestId);
      const found = store.hosts.find((item) => item.deptId === Number(hostId[1]));
      if (!found) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      return ok(requestId, found);
    }
    if (hostId && method === "PUT") {
      requireOps(init, requestId);
      const payload = body as unknown as UpdateHostRequest;
      if (!payload.reason?.trim()) {
        return fail(requestId, 400, 400, "必须填写操作理由");
      }
      const index = store.hosts.findIndex((item) => item.deptId === Number(hostId[1]));
      if (index < 0) {
        return fail(requestId, 404, 404, "资源不存在或因数据权限不可见");
      }
      store.hosts[index] = {
        ...store.hosts[index],
        hostName: payload.hostName ?? store.hosts[index].hostName,
        dyAccount: payload.dyAccount ?? store.hosts[index].dyAccount,
        logoUrl: payload.logoUrl ?? store.hosts[index].logoUrl,
        contact: payload.contact ?? store.hosts[index].contact,
        remark: payload.remark ?? store.hosts[index].remark,
      };
      return ok(requestId, store.hosts[index]);
    }

    return fail(requestId, 404, 404, `未实现的 Mock 路径 ${method} ${url}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return fail(requestId, error.httpStatus, error.code, error.message);
    }
    return fail(requestId, 400, 400, error instanceof Error ? error.message : "请求失败");
  }
}

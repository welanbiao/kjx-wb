import type {
  AdminAppeal,
  AdminBinding,
  AdminHost,
  AdminMember,
  AdminSession,
  AdminWork,
  CreateHostMemberData,
  HostSelfInfo,
  MemberStatus,
  UnlockRecord,
} from "../lib/types";

export type MockPersona = {
  username: string;
  password: string;
  session: Omit<AdminSession, "token">;
};

export const MOCK_PERSONAS: MockPersona[] = [
  {
    username: "host-a",
    password: "mock-host-a",
    session: { username: "host-a", userId: 201, deptId: 101, roles: ["HOST"] },
  },
  {
    username: "host-b",
    password: "mock-host-b",
    session: { username: "host-b", userId: 202, deptId: 102, roles: ["HOST"] },
  },
  {
    username: "operator",
    password: "mock-operator",
    session: { username: "operator", userId: 301, deptId: 1, roles: ["OPERATOR"] },
  },
  {
    username: "superadmin",
    password: "mock-superadmin",
    session: { username: "superadmin", userId: 1, deptId: 1, roles: ["superadmin"] },
  },
];

export type MockEntitlement = {
  memberId: number;
  workCode: string;
  source: "user" | "official_add";
};

export type MockState = {
  nextMemberId: number;
  nextBindingId: number;
  nextAppealId: number;
  nextWorkId: number;
  nextUnlockId: number;
  nextHostDeptId: number;
  hosts: AdminHost[];
  members: AdminMember[];
  bindings: AdminBinding[];
  appeals: AdminAppeal[];
  works: AdminWork[];
  unlocks: UnlockRecord[];
  entitlements: MockEntitlement[];
  tokens: Map<string, AdminSession>;
};

function iso(daysFromNow: number): string {
  const date = new Date(Date.UTC(2027, 7, 9 + daysFromNow, 15, 59, 59, 999));
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, "0");
  const d = String(date.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${d}T23:59:59.999+08:00`;
}

function seedMembers(): AdminMember[] {
  return [
    {
      id: 1001,
      memberCode: "KJX-A3F9K2B7",
      douyinId: "zhangsan",
      deptId: 101,
      hostCode: "KJX",
      subscribedMonths: 15,
      subscribedDays: 435,
      expireAt: iso(0),
      startAt: "2026-05-01T00:00:00.000+08:00",
      availableCount: 14,
      unlockedCount: 2,
      remainCount: 12,
      status: "VALID",
      bindState: 1,
      boundAccountId: 9001,
      boundAt: "2026-06-01T12:00:00.000+08:00",
      remark: "A 部门样例",
      createTime: "2026-05-01T10:00:00.000+08:00",
    },
    {
      id: 1002,
      memberCode: "XYZ-B7K2F9A3",
      douyinId: "lisi",
      deptId: 102,
      hostCode: "XYZ",
      subscribedMonths: 8,
      subscribedDays: 240,
      expireAt: iso(30),
      startAt: "2026-04-01T00:00:00.000+08:00",
      availableCount: 8,
      unlockedCount: 0,
      remainCount: 8,
      status: "VALID",
      bindState: 0,
      boundAccountId: null,
      boundAt: null,
      remark: "B 部门样例",
      createTime: "2026-04-01T10:00:00.000+08:00",
    },
  ];
}

function seed(): MockState {
  return {
    nextMemberId: 1100,
    nextBindingId: 2100,
    nextAppealId: 3100,
    nextWorkId: 4100,
    nextUnlockId: 5100,
    nextHostDeptId: 200,
    hosts: [
      {
        deptId: 101,
        hostCode: "KJX",
        hostName: "叩君芯",
        dyAccount: "kjx-official",
        logoUrl: null,
        contact: "运营对接",
        remark: "HOST A",
      },
      {
        deptId: 102,
        hostCode: "XYZ",
        hostName: "星河主播",
        dyAccount: "xyz-live",
        logoUrl: null,
        contact: null,
        remark: "HOST B",
      },
    ],
    members: seedMembers(),
    bindings: [
      {
        id: 2001,
        deptId: 101,
        accountId: 9001,
        memberId: 1001,
        memberCode: "KJX-A3F9K2B7",
        douyinIdInput: "zhangsan",
        claimMonths: 15,
        status: "MATCHED",
        rejectReason: null,
        matchedAt: "2026-06-01T12:00:00.000+08:00",
        createTime: "2026-06-01T11:00:00.000+08:00",
      },
      {
        id: 2002,
        deptId: 101,
        accountId: 9003,
        memberId: 1001,
        memberCode: "KJX-A3F9K2B7",
        douyinIdInput: "zhangsan",
        claimMonths: 15,
        status: "PENDING_REVIEW",
        rejectReason: null,
        matchedAt: null,
        createTime: "2026-09-01T09:00:00.000+08:00",
      },
      {
        id: 2003,
        deptId: 102,
        accountId: 9004,
        memberId: 1002,
        memberCode: "XYZ-B7K2F9A3",
        douyinIdInput: "lisi",
        claimMonths: 8,
        status: "PENDING_REVIEW",
        rejectReason: null,
        matchedAt: null,
        createTime: "2026-09-02T09:00:00.000+08:00",
      },
    ],
    appeals: [
      {
        id: 3001,
        accountId: 9004,
        bindingId: 2003,
        appealType: "WRONG_BIND",
        content: "绑错主播",
        contact: null,
        status: "PENDING",
        handleNote: null,
        createTime: "2026-09-03T08:00:00.000+08:00",
      },
    ],
    works: [
      {
        id: 4001,
        workCode: "breakup-editor",
        name: "分镜编辑器",
        workType: "layout",
        summary: "示例作品",
        coverUrl: null,
        category: "tool",
        tags: ["m1"],
        status: "PUBLISHED",
        currentVersion: "1.0.0",
        contentVersion: "c1",
        accessForm: "A",
        grayRatio: 0,
        sortWeight: 10,
        publishTime: "2026-06-01T00:00:00.000+08:00",
        createTime: "2026-05-01T00:00:00.000+08:00",
      },
      {
        id: 4002,
        workCode: "gray-lab",
        name: "灰度实验室",
        workType: "game",
        summary: null,
        coverUrl: null,
        category: null,
        tags: [],
        status: "DRAFT",
        currentVersion: null,
        contentVersion: null,
        accessForm: "A",
        grayRatio: 20,
        sortWeight: 0,
        publishTime: null,
        createTime: "2026-08-01T00:00:00.000+08:00",
      },
    ],
    unlocks: [
      {
        id: 5001,
        deptId: 101,
        memberId: 1001,
        memberCode: "KJX-A3F9K2B7",
        douyinId: "zhangsan",
        accountId: 9001,
        workId: 4001,
        workCode: "breakup-editor",
        workName: "分镜编辑器",
        workVersion: "1.0.0",
        unlockTime: "2026-07-01T10:00:00.000+08:00",
        beforeUnlockedCount: 1,
        afterUnlockedCount: 2,
        beforeRemainCount: 13,
        afterRemainCount: 12,
        result: "SUCCESS",
        failReason: null,
        source: "user",
        operatorId: null,
      },
      {
        id: 5002,
        deptId: 102,
        memberId: 1002,
        memberCode: "XYZ-B7K2F9A3",
        douyinId: "lisi",
        accountId: null,
        workId: 4001,
        workCode: "breakup-editor",
        workName: "分镜编辑器",
        workVersion: "1.0.0",
        unlockTime: "2026-07-02T10:00:00.000+08:00",
        result: "FAILED",
        failReason: "quota",
        source: "user",
        operatorId: null,
      },
    ],
    entitlements: [{ memberId: 1001, workCode: "breakup-editor", source: "user" }],
    tokens: new Map(),
  };
}

let store: MockState = seed();

export function getMockStore(): MockState {
  return store;
}

export function resetMockStore(): MockState {
  store = seed();
  return store;
}

export function personaByUsername(username: string): MockPersona | undefined {
  return MOCK_PERSONAS.find((item) => item.username === username);
}

export function loginMock(username: string, password: string): AdminSession {
  const persona = MOCK_PERSONAS.find((item) => item.username === username && item.password === password);
  if (!persona) {
    throw new Error("账号或密码错误");
  }
  const token = `mock-${persona.username}-${crypto.randomUUID()}`;
  const session: AdminSession = { ...persona.session, token };
  store.tokens.set(token, session);
  return session;
}

export function sessionFromToken(token: string | null): AdminSession | null {
  if (!token) {
    return null;
  }
  const existing = store.tokens.get(token);
  if (existing) {
    return existing;
  }
  const match = /^mock-(host-a|host-b|operator|superadmin)-/.exec(token);
  if (!match) {
    return null;
  }
  const persona = personaByUsername(match[1]);
  if (!persona) {
    return null;
  }
  const session: AdminSession = { ...persona.session, token };
  store.tokens.set(token, session);
  return session;
}

export function isOpsSession(session: AdminSession): boolean {
  return session.roles.includes("OPERATOR") || session.roles.includes("superadmin");
}

export function isHostSession(session: AdminSession): boolean {
  return session.roles.includes("HOST") && !isOpsSession(session);
}

export function hostCodeOf(memberCode: string): string {
  const dash = memberCode.indexOf("-");
  return dash > 0 ? memberCode.slice(0, dash) : memberCode;
}

export function floorAvailable(days: number): number {
  return Math.floor(days / 30);
}

export function memberStatus(expireAt: string, startAt: string | null, now = "2026-09-22T00:00:00.000+08:00"): MemberStatus {
  if (startAt && now < startAt) {
    return "NOT_STARTED";
  }
  if (now > expireAt) {
    return "EXPIRED";
  }
  return "VALID";
}

export function createdView(member: AdminMember): CreateHostMemberData {
  return {
    memberId: member.id,
    memberCode: member.memberCode,
    availableCount: member.availableCount,
    status: member.status,
  };
}

export function asHostSelf(host: AdminHost): HostSelfInfo {
  return {
    deptId: host.deptId,
    hostCode: host.hostCode,
    hostName: host.hostName,
    dyAccount: host.dyAccount,
    logoUrl: host.logoUrl,
    contact: host.contact,
  };
}

export function randomMemberSuffix(): string {
  const alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  let out = "";
  for (let i = 0; i < 8; i += 1) {
    out += alphabet[Math.floor(Math.random() * alphabet.length)];
  }
  return out;
}


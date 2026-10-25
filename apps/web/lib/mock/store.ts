import type { MockCaptchaRecord } from "@/lib/mock/captcha";
import type {
  AccountProfile,
  BindingStatus,
  BindingStatusData,
  DeviceItem,
  HostOption,
  MemberSnapshot,
  MemberStatus,
  WorkDetail,
} from "@/lib/types";
import { maskPhone } from "@/lib/format";

export const MOCK_SMS_CODE = "123456";
export const MOCK_PASSWORD = "Passw0rd1";

export type MockAccount = {
  id: number;
  phone: string;
  password: string;
  nickname: string | null;
  avatar: string | null;
  status: 1 | 2 | 3 | 4;
  bindStatus: 0 | 1 | 2 | 3 | 4;
  cancelApplyAt: string | null;
  lastLoginAt: string | null;
  createTime: string;
  loginFailures: number;
  lockedUntil: number | null;
};

export type MockMember = {
  memberCode: string;
  douyinId: string;
  hostCode: string;
  claimMonths: number;
  subscribedMonths: number;
  subscribedDays: number;
  availableCount: number;
  unlockedCount: number;
  remainCount: number;
  status: MemberStatus;
  startAt: string | null;
  expireAt: string;
  boundAccountId: number | null;
};

export type MockBinding = {
  accountId: number;
  status: BindingStatus;
  submitAt: string | null;
  rejectReason: string | null;
  hostCode: string | null;
  douyinId: string | null;
  memberCode: string | null;
  claimMonths: number | null;
};

export type MockEntitlement = {
  accountId: number;
  workCode: string;
  name: string;
  coverUrl: string | null;
  workType: "layout" | "game";
  workStatus: WorkDetail["status"];
  unlockedAt: string;
  source: "user" | "official_add" | "purchase";
  entryUrl: string;
};

export type MockWork = WorkDetail;

export type MockDevice = Omit<DeviceItem, "current"> & {
  accountId: number;
};

export type MockStore = {
  nextAccountId: number;
  nextDeviceId: number;
  accounts: MockAccount[];
  sessions: Map<string, { accountId: number; createdAt: number }>;
  sms: Map<string, { code: string; scene: string; expireAt: number }>;
  members: MockMember[];
  bindings: MockBinding[];
  works: MockWork[];
  entitlements: MockEntitlement[];
  hosts: HostOption[];
  devices: MockDevice[];
  captchas: Map<string, MockCaptchaRecord>;
  idempotency: Map<string, { hash: string; body: unknown; status: number }>;
  bindFailures: Map<number, { count: number; lockedUntil: number | null }>;
  autoReview: boolean;
  runtimeSessions: MockRuntimeSession[];
  aiSettings: Map<number, { baseUrl: string; model: string; apiKey: string }>;
};

export type MockRuntimeSession = {
  sessionId: string;
  accountId: number;
  workCode: string;
  trial: boolean;
  payload: Record<string, unknown>;
  checksum: string;
  status: "ACTIVE" | "ENDED";
  schemaVersion: string;
  form: "A";
  stage: string;
  version: number;
};

function iso(daysFromNow: number, endOfDay = false): string {
  const date = new Date();
  date.setDate(date.getDate() + daysFromNow);
  if (endOfDay) {
    date.setHours(23, 59, 59, 999);
  }
  const offset = "+08:00";
  const pad = (n: number, w = 2) => String(n).padStart(w, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}${offset}`;
}

export const SEEDED_VALID_MEMBER: MockMember = {
  memberCode: "KJX-A3F9K2B7",
  douyinId: "zhangsan",
  hostCode: "KJX",
  claimMonths: 15,
  subscribedMonths: 15,
  subscribedDays: 435,
  availableCount: 14,
  unlockedCount: 0,
  remainCount: 14,
  status: "VALID",
  startAt: iso(-120),
  expireAt: iso(360, true),
  boundAccountId: null,
};

export const SEEDED_EXPIRED_MEMBER: MockMember = {
  memberCode: "KJX-B2C4D6E8",
  douyinId: "expireduser",
  hostCode: "KJX",
  claimMonths: 12,
  subscribedMonths: 12,
  subscribedDays: 365,
  availableCount: 12,
  unlockedCount: 1,
  remainCount: 11,
  status: "EXPIRED",
  startAt: iso(-400),
  expireAt: iso(-10, true),
  boundAccountId: 9001,
};

const WORKS: MockWork[] = [
  {
    workCode: "breakup-editor",
    name: "分手结局修改器",
    workType: "game",
    summary: "如果那句话没说出口，他们还会分手吗？",
    coverUrl: null,
    category: "layout",
    tags: ["会话", "布局"],
    status: "PUBLISHED",
    currentVersion: "1.0.0",
    accessForm: "A",
    unlocked: false,
    unlockedAt: null,
    screenshots: [],
    allowedChannels: ["PLATFORM", "BYOK"],
    contentVersion: "1.0.0",
    entryUrl: null,
    publishTime: iso(-20),
  },
  {
    workCode: "dream-phone",
    name: "梦女小手机",
    workType: "game",
    summary: "一部微信外壳的梦女手机：聊天、朋友圈、钱包与生活应用。",
    coverUrl: null,
    category: "game",
    tags: ["文游", "手机"],
    status: "PUBLISHED",
    currentVersion: "1.0.0",
    accessForm: "A",
    unlocked: false,
    unlockedAt: null,
    screenshots: [],
    allowedChannels: ["PLATFORM", "BYOK"],
    contentVersion: "1.0.0",
    entryUrl: null,
    publishTime: iso(-15),
  },
  {
    workCode: "midnight-script",
    name: "午夜剧本",
    workType: "game",
    summary: "AI 文游。解锁后进入游戏游玩界面。",
    coverUrl: null,
    category: "game",
    tags: ["文游"],
    status: "PUBLISHED",
    currentVersion: "1.2.0",
    accessForm: "A",
    unlocked: false,
    unlockedAt: null,
    screenshots: [],
    allowedChannels: ["PLATFORM", "BYOK"],
    contentVersion: "1.2.0",
    entryUrl: null,
    publishTime: iso(-40),
  },
  {
    workCode: "archived-stage",
    name: "已下架剧场",
    workType: "game",
    summary: "已下架作品。无权益不可见；有权益仍可从我的解锁进入。",
    coverUrl: null,
    category: "game",
    tags: ["下架"],
    status: "UNPUBLISHED",
    currentVersion: "0.9.0",
    accessForm: "A",
    unlocked: false,
    unlockedAt: null,
    screenshots: [],
    allowedChannels: ["PLATFORM"],
    contentVersion: "0.9.0",
    entryUrl: null,
    publishTime: iso(-90),
  },
  {
    workCode: "gray-preview",
    name: "灰度预演",
    workType: "layout",
    summary: "GRAY 作品。Mock 全员可见；live 由后端 CRC32 分桶，前端不计算。",
    coverUrl: null,
    category: "layout",
    tags: ["灰度"],
    status: "GRAY",
    currentVersion: "0.8.0",
    accessForm: "A",
    unlocked: false,
    unlockedAt: null,
    screenshots: [],
    allowedChannels: ["PLATFORM"],
    contentVersion: "0.8.0",
    entryUrl: null,
    publishTime: iso(-5),
  },
];

function seedAccounts(): MockAccount[] {
  return [
    {
      id: 9001,
      phone: "13900139000",
      password: MOCK_PASSWORD,
      nickname: "过期会员",
      avatar: null,
      status: 1,
      bindStatus: 2,
      cancelApplyAt: null,
      lastLoginAt: iso(-1),
      createTime: iso(-400),
      loginFailures: 0,
      lockedUntil: null,
    },
    {
      id: 9002,
      phone: "13900139001",
      password: MOCK_PASSWORD,
      nickname: "驳回账号",
      avatar: null,
      status: 1,
      bindStatus: 3,
      cancelApplyAt: null,
      lastLoginAt: iso(-2),
      createTime: iso(-30),
      loginFailures: 0,
      lockedUntil: null,
    },
    {
      id: 9003,
      phone: "13900139002",
      password: MOCK_PASSWORD,
      nickname: "解绑中账号",
      avatar: null,
      status: 1,
      bindStatus: 4,
      cancelApplyAt: null,
      lastLoginAt: iso(-3),
      createTime: iso(-60),
      loginFailures: 0,
      lockedUntil: null,
    },
  ];
}

export function createInitialStore(): MockStore {
  const expiredUnlockAt = iso(-30);
  return {
    nextAccountId: 10001,
    nextDeviceId: 1,
    accounts: seedAccounts(),
    sessions: new Map(),
    sms: new Map(),
    captchas: new Map(),
    members: [
      { ...SEEDED_VALID_MEMBER, boundAccountId: null },
      { ...SEEDED_EXPIRED_MEMBER, boundAccountId: 9001 },
    ],
    bindings: [
      {
        accountId: 9001,
        status: "MATCHED",
        submitAt: iso(-200),
        rejectReason: null,
        hostCode: "KJX",
        douyinId: "expireduser",
        memberCode: "KJX-B2C4D6E8",
        claimMonths: 12,
      },
      {
        accountId: 9002,
        status: "REJECTED",
        submitAt: iso(-3),
        rejectReason: "三要素与主播底稿不一致",
        hostCode: "KJX",
        douyinId: "wrongid",
        memberCode: "KJX-A3F9K2B7",
        claimMonths: 3,
      },
      {
        accountId: 9003,
        status: "UNBINDING",
        submitAt: iso(-10),
        rejectReason: null,
        hostCode: "KJX",
        douyinId: "leaving",
        memberCode: "KJX-C3D5F7G8",
        claimMonths: 6,
      },
    ],
    works: WORKS.map((work) => ({ ...work })),
    entitlements: [
      {
        accountId: 9001,
        workCode: "midnight-script",
        name: "午夜剧本",
        coverUrl: null,
        workType: "game",
        workStatus: "PUBLISHED",
        unlockedAt: expiredUnlockAt,
        source: "user",
        entryUrl: "/runtime/midnight-script",
      },
      {
        accountId: 9001,
        workCode: "archived-stage",
        name: "已下架剧场",
        coverUrl: null,
        workType: "game",
        workStatus: "UNPUBLISHED",
        unlockedAt: iso(-80),
        source: "official_add",
        entryUrl: "/runtime/archived-stage",
      },
    ],
    hosts: [{ hostCode: "KJX", hostName: "叩君芯" }],
    devices: [],
    idempotency: new Map(),
    bindFailures: new Map(),
    autoReview: false,
    runtimeSessions: [],
    aiSettings: new Map(),
  };
}

let store: MockStore = createInitialStore();

export function getStore(): MockStore {
  return store;
}

export function resetStore(): MockStore {
  store = createInitialStore();
  return store;
}

export function snapshotMember(member: MockMember): MemberSnapshot {
  return {
    memberCode: member.memberCode,
    douyinId: member.douyinId,
    expireAt: member.expireAt,
    startAt: member.startAt,
    availableCount: member.availableCount,
    unlockedCount: member.unlockedCount,
    remainCount: member.remainCount,
    status: member.status,
    subscribedMonths: member.subscribedMonths,
    subscribedDays: member.subscribedDays,
  };
}

export function toProfile(account: MockAccount): AccountProfile {
  return {
    id: account.id,
    nickname: account.nickname,
    avatar: account.avatar,
    phoneMask: maskPhone(account.phone),
    status: account.status,
    bindStatus: account.bindStatus,
    cancelApplyAt: account.cancelApplyAt,
    lastLoginAt: account.lastLoginAt,
    createTime: account.createTime,
  };
}

export function toBindingStatus(accountId: number): BindingStatusData {
  const binding = store.bindings.find((item) => item.accountId === accountId);
  if (!binding) {
    return { status: "NONE", submitAt: null, rejectReason: null, member: null };
  }
  const member =
    binding.status === "MATCHED"
      ? store.members.find((item) => item.boundAccountId === accountId) ?? null
      : null;
  return {
    status: binding.status,
    submitAt: binding.submitAt,
    rejectReason: binding.rejectReason,
    member: member ? snapshotMember(member) : null,
  };
}

export function bindStatusNumber(status: BindingStatus): 0 | 1 | 2 | 3 | 4 {
  switch (status) {
    case "NONE":
      return 0;
    case "PENDING_REVIEW":
      return 1;
    case "MATCHED":
      return 2;
    case "REJECTED":
      return 3;
    case "UNBINDING":
      return 4;
    default:
      return 0;
  }
}

export function daysFromNowIso(days: number, endOfDay = false): string {
  return iso(days, endOfDay);
}

export function nowIso(): string {
  return iso(0);
}

export function entryUrlFor(workCode: string): string {
  return `/runtime/${workCode}`;
}

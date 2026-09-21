/** OpenAPI-aligned C-end types. Field names must match contracts/openapi. */

export type ApiEnvelope<T> = {
  code: number;
  data: T;
  msg: string;
  requestId: string;
};

export type BindStatus = 0 | 1 | 2 | 3 | 4;

export type BindingStatus =
  | "NONE"
  | "PENDING_REVIEW"
  | "MATCHED"
  | "REJECTED"
  | "UNBINDING";

export type MemberStatus = "NOT_STARTED" | "VALID" | "EXPIRED";

export type AccountStatus = 1 | 2 | 3 | 4;

export type WorkType = "layout" | "game";

export type WorkStatus =
  | "DRAFT"
  | "PENDING_REVIEW"
  | "PUBLISHED"
  | "GRAY"
  | "UNPUBLISHED"
  | "REJECTED";

export type AccessForm = "A" | "B" | "C";

export type SendCodeScene = "register" | "login" | "reset_pwd" | "cancel";

export type AccountBrief = {
  id: number;
  nickname: string | null;
  avatar: string | null;
  phoneMask: string | null;
};

export type MemberSnapshot = {
  memberCode: string;
  douyinId: string | null;
  expireAt: string;
  startAt: string | null;
  availableCount: number;
  unlockedCount: number;
  remainCount: number;
  status: MemberStatus;
  subscribedMonths?: number;
  subscribedDays?: number;
};

export type LoginData = {
  expireIn: number;
  account: AccountBrief;
  bindStatus: BindStatus;
  member: MemberSnapshot | null;
};

export type RegisterData = {
  accountId: number;
};

export type AccountProfile = {
  id: number;
  nickname: string | null;
  avatar: string | null;
  phoneMask: string | null;
  status: AccountStatus;
  bindStatus: BindStatus;
  cancelApplyAt: string | null;
  lastLoginAt: string | null;
  createTime: string;
};

export type BindingStatusData = {
  status: BindingStatus;
  submitAt: string | null;
  rejectReason: string | null;
  member: MemberSnapshot | null;
};

export type SubmitBindingData = {
  status: BindingStatus;
  member: MemberSnapshot | null;
};

export type HostOption = {
  hostCode: string;
  hostName: string;
};

export type WorkSummary = {
  workCode: string;
  name: string;
  workType: WorkType;
  summary: string | null;
  coverUrl: string | null;
  category: string | null;
  tags: string[];
  status: WorkStatus;
  currentVersion: string | null;
  accessForm?: AccessForm;
  unlocked: boolean;
  unlockedAt: string | null;
};

export type WorkDetail = WorkSummary & {
  screenshots?: string[];
  allowedChannels?: Array<"PLATFORM" | "BYOK">;
  contentVersion: string | null;
  entryUrl: string | null;
  publishTime: string | null;
};

export type PageResult<T> = {
  list: T[];
  total: number;
};

export type UnlockResult = {
  workCode: string;
  unlockedCount: number;
  remainCount: number;
  entryUrl?: string;
  alreadyUnlocked: boolean;
};

export type EntitlementItem = {
  workCode: string;
  name: string;
  coverUrl: string | null;
  workType?: WorkType;
  workStatus: WorkStatus;
  unlockedAt: string;
  source: "user" | "official_add";
  entryUrl?: string;
};

export type DeviceItem = {
  id: number;
  deviceId: string;
  deviceName: string | null;
  platform: "web" | "ios" | "android" | null;
  lastRegion: string | null;
  lastActiveAt: string | null;
  current: boolean;
};

export type CancelAccountData = {
  cancelApplyAt: string;
  effectiveAt: string;
};

export type {
  CaptchaChallenge,
  CaptchaType,
  FrozenCaptcha,
  ImageCaptchaTrack,
  ImageCaptchaTrackPoint,
} from "@/lib/captcha/types";

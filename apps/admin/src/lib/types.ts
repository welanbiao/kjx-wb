/** OpenAPI-aligned admin types. Field names must match contracts/openapi. */

export type ApiEnvelope<T> = {
  code: number;
  data: T;
  msg: string;
  requestId: string;
};

export type PageResult<T> = {
  list: T[];
  total: number;
};

export type MemberStatus = "NOT_STARTED" | "VALID" | "EXPIRED";
export type BindingReviewStatus = "PENDING_REVIEW" | "MATCHED" | "REJECTED" | "UNBINDING";
export type AppealStatus = "PENDING" | "APPROVED" | "REJECTED";
export type AppealType = "CHANGE_DOUYIN" | "WRONG_BIND" | "OTHER";
export type WorkType = "layout" | "game";
export type WorkStatus =
  | "DRAFT"
  | "PENDING_REVIEW"
  | "PUBLISHED"
  | "GRAY"
  | "UNPUBLISHED"
  | "REJECTED";
export type AccessForm = "A" | "B" | "C";
export type UnlockSource = "user" | "official_add" | "official_remove" | "fix";
export type UnlockResult = "SUCCESS" | "FAILED";
export type EntitlementAction = "ADD" | "REMOVE";
export type BindState = 0 | 1;

export type RoleKey = "HOST" | "OPERATOR" | "superadmin" | "PUBLISHER" | "FINANCE";

export type AdminSession = {
  token: string;
  username: string;
  userId: number;
  deptId: number | null;
  roles: RoleKey[];
};

export type HostSelfInfo = {
  deptId: number;
  hostCode: string;
  hostName: string;
  dyAccount: string | null;
  logoUrl: string | null;
  contact: string | null;
};

export type AdminHost = HostSelfInfo & {
  remark: string | null;
};

export type AdminMember = {
  id: number;
  memberCode: string;
  douyinId: string;
  deptId: number;
  hostCode?: string;
  subscribedMonths: number;
  subscribedDays: number;
  expireAt: string;
  startAt: string | null;
  availableCount: number;
  unlockedCount: number;
  remainCount: number;
  status: MemberStatus;
  bindState: BindState;
  boundAccountId: number | null;
  boundAt: string | null;
  remark: string | null;
  createTime?: string;
};

export type CreateHostMemberRequest = {
  douyinId: string;
  subscribedMonths: number;
  subscribedDays: number;
  expireAt: string;
  remark?: string;
};

export type CreateOpsMemberRequest = CreateHostMemberRequest & {
  deptId: number;
  reason: string;
};

export type CreateHostMemberData = {
  memberId: number;
  memberCode: string;
  availableCount: number;
  status: MemberStatus;
};

export type UpdateHostMemberRequest = {
  subscribedMonths?: number;
  subscribedDays?: number;
  expireAt?: string;
  remark?: string;
  reason: string;
};

export type AdminBinding = {
  id: number;
  deptId: number;
  accountId: number;
  memberId: number;
  memberCode?: string;
  douyinIdInput?: string;
  claimMonths: number;
  status: BindingReviewStatus;
  rejectReason: string | null;
  matchedAt: string | null;
  createTime: string;
};

export type AdminAppeal = {
  id: number;
  accountId: number;
  bindingId: number | null;
  appealType: AppealType;
  content: string;
  contact: string | null;
  status: AppealStatus;
  handleNote: string | null;
  createTime: string;
};

export type AdminWork = {
  id: number;
  workCode: string;
  name: string;
  workType: WorkType;
  summary: string | null;
  coverUrl: string | null;
  category: string | null;
  tags?: string[];
  status: WorkStatus;
  currentVersion: string | null;
  contentVersion: string | null;
  accessForm?: AccessForm;
  grayRatio?: number;
  sortWeight?: number;
  publishTime: string | null;
  createTime?: string;
};

export type CreateWorkRequest = {
  workCode: string;
  name: string;
  workType: WorkType;
  summary?: string;
  coverUrl?: string;
  category?: string;
  tags?: string[];
  accessForm?: AccessForm;
  grayRatio?: number;
  sortWeight?: number;
  reason: string;
};

export type UpdateWorkRequest = {
  name?: string;
  summary?: string;
  coverUrl?: string;
  category?: string;
  tags?: string[];
  status?: WorkStatus;
  accessForm?: AccessForm;
  grayRatio?: number;
  sortWeight?: number;
  reason: string;
};

export type UnlockRecord = {
  id: number;
  deptId: number;
  memberId: number;
  memberCode?: string;
  douyinId?: string | null;
  accountId?: number | null;
  workId?: number;
  workCode: string;
  workName?: string | null;
  workVersion?: string | null;
  unlockTime: string;
  beforeUnlockedCount?: number;
  afterUnlockedCount?: number;
  beforeRemainCount?: number;
  afterRemainCount?: number;
  result: UnlockResult;
  failReason?: string | null;
  source: UnlockSource;
  operatorId?: number | null;
};

export type CreateHostRequest = {
  hostCode: string;
  hostName: string;
  parentDeptId?: number;
  adminUserId: number;
  dyAccount?: string;
  logoUrl?: string;
  contact?: string;
  remark?: string;
  reason: string;
};

export type UpdateHostRequest = {
  hostName?: string;
  dyAccount?: string;
  logoUrl?: string;
  contact?: string;
  remark?: string;
  reason: string;
};

export type AdjustEntitlementRequest = {
  action: EntitlementAction;
  workCode: string;
  reason: string;
};

export type RecalcMemberRequest = {
  reason: string;
};

export type ReviewReasonRequest = {
  reason: string;
};

export type ApproveBindingRequest = {
  remark?: string;
};

export type HandleAppealRequest = {
  result: "APPROVED" | "REJECTED";
  handleNote: string;
};

export type MemberPageQuery = {
  deptId?: number;
  douyinId?: string;
  memberCode?: string;
  status?: MemberStatus;
  bindState?: BindState;
  pageNo?: number;
  pageSize?: number;
};

export type BindingPageQuery = {
  status?: BindingReviewStatus;
  deptId?: number;
  memberCode?: string;
  pageNo?: number;
  pageSize?: number;
};

export type UnlockRecordQuery = {
  deptId?: number;
  memberId?: number;
  workCode?: string;
  result?: UnlockResult;
  source?: UnlockSource;
  pageNo?: number;
  pageSize?: number;
};

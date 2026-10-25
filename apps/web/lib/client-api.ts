import { apiFetch } from "@/lib/api";
import { isMockToolsEnabled } from "@/lib/flags";
import type { CaptchaChallenge, FrozenCaptcha } from "@/lib/captcha/types";
import type {
  AccountProfile,
  BindingStatusData,
  CancelAccountData,
  DeviceItem,
  EntitlementItem,
  HostOption,
  LoginData,
  MemberSnapshot,
  PageResult,
  RegisterData,
  SubmitBindingData,
  UnlockResult,
  WorkDetail,
  WorkSummary,
  AiSettingsView,
} from "@/lib/types";

function idemKey(): string {
  return crypto.randomUUID().replace(/-/g, "").slice(0, 32);
}

export const clientApi = {
  csrf: () => apiFetch<{ csrfToken: string }>("/app/auth/csrf"),
  getCaptcha: (type: "SLIDER" = "SLIDER") =>
    apiFetch<CaptchaChallenge>(`/app/auth/captcha?type=${encodeURIComponent(type)}&t=${Date.now()}`),
  checkCaptcha: (body: FrozenCaptcha) =>
    apiFetch<{ passed: boolean }>("/app/auth/captcha/check", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  sendCode: (body: {
    phone: string;
    scene: "register" | "login" | "reset_pwd" | "cancel";
    captchaId: string;
    captchaData: FrozenCaptcha["captchaData"];
  }) =>
    apiFetch<{ expireIn: number }>("/app/auth/send-code", {
      method: "POST",
      body: JSON.stringify(body),
      idempotencyKey: idemKey(),
    }),
  register: (body: { phone: string; password: string; code: string; agreeTerms: true }) =>
    apiFetch<RegisterData>("/app/auth/register", {
      method: "POST",
      body: JSON.stringify(body),
      idempotencyKey: idemKey(),
    }),
  login: (body: {
    phone: string;
    password: string;
    captchaId?: string;
    captchaData?: FrozenCaptcha["captchaData"];
  }) =>
    apiFetch<LoginData>("/app/auth/login", {
      method: "POST",
      body: JSON.stringify(body),
      idempotencyKey: idemKey(),
    }),
  loginByCode: (body: { phone: string; code: string }) =>
    apiFetch<LoginData>("/app/auth/login-by-code", {
      method: "POST",
      body: JSON.stringify(body),
      idempotencyKey: idemKey(),
    }),
  logout: () => apiFetch<Record<string, never>>("/app/auth/logout", { method: "POST" }),
  refresh: () => apiFetch<LoginData>("/app/auth/refresh", { method: "POST" }),
  resetPassword: (body: { phone: string; code: string; newPassword: string }) =>
    apiFetch<Record<string, never>>("/app/auth/reset-password", {
      method: "POST",
      body: JSON.stringify(body),
      idempotencyKey: idemKey(),
    }),
  profile: () => apiFetch<AccountProfile>("/app/account/profile"),
  updateProfile: (body: { nickname?: string; avatar?: string }) =>
    apiFetch<AccountProfile>("/app/account/profile", { method: "PUT", body: JSON.stringify(body) }),
  changePassword: (body: { oldPassword: string; newPassword: string }) =>
    apiFetch<Record<string, never>>("/app/account/password", {
      method: "PUT",
      body: JSON.stringify(body),
      idempotencyKey: idemKey(),
    }),
  devices: () => apiFetch<DeviceItem[]>("/app/account/devices"),
  deleteDevice: (id: number) =>
    apiFetch<Record<string, never>>(`/app/account/devices/${id}`, {
      method: "DELETE",
      idempotencyKey: idemKey(),
    }),
  cancelAccount: (body: { code: string; reason?: string }) =>
    apiFetch<CancelAccountData>("/app/account/cancel", {
      method: "POST",
      body: JSON.stringify(body),
      idempotencyKey: idemKey(),
    }),
  revokeCancel: () => apiFetch<Record<string, never>>("/app/account/cancel/revoke", { method: "POST" }),
  hosts: () => apiFetch<HostOption[]>("/app/member/hosts"),
  binding: () => apiFetch<BindingStatusData>("/app/member/binding"),
  memberProfile: () => apiFetch<MemberSnapshot | null>("/app/member/profile"),
  submitBinding: (body: { hostCode?: string; douyinId: string; memberCode: string; claimMonths: number }) =>
    apiFetch<SubmitBindingData>("/app/member/binding", {
      method: "POST",
      body: JSON.stringify(body),
      idempotencyKey: idemKey(),
    }),
  appeal: (body: { appealType: "CHANGE_DOUYIN" | "WRONG_BIND" | "OTHER"; content: string; contact?: string }) =>
    apiFetch<{ appealId: number; status: "PENDING" }>("/app/member/binding/appeal", {
      method: "POST",
      body: JSON.stringify(body),
      idempotencyKey: idemKey(),
    }),
  works: (query?: { workType?: string; unlocked?: boolean; pageNo?: number; pageSize?: number }) => {
    const params = new URLSearchParams();
    if (query?.workType) params.set("workType", query.workType);
    if (typeof query?.unlocked === "boolean") params.set("unlocked", String(query.unlocked));
    if (query?.pageNo) params.set("pageNo", String(query.pageNo));
    if (query?.pageSize) params.set("pageSize", String(query.pageSize));
    const suffix = params.toString() ? `?${params.toString()}` : "";
    return apiFetch<PageResult<WorkSummary>>(`/app/works${suffix}`);
  },
  work: (workCode: string) => apiFetch<WorkDetail>(`/app/works/${workCode}`),
  unlock: (workCode: string) =>
    apiFetch<UnlockResult>(`/app/works/${workCode}/unlock`, {
      method: "POST",
      idempotencyKey: idemKey(),
    }),
  purchase: (workCode: string) =>
    apiFetch<UnlockResult>(`/app/works/${workCode}/purchase`, {
      method: "POST",
      idempotencyKey: idemKey(),
    }),
  aiSettings: () => apiFetch<AiSettingsView>("/app/account/ai-settings"),
  saveAiSettings: (body: { baseUrl: string; apiKey: string; model: string }) =>
    apiFetch<AiSettingsView>("/app/account/ai-settings", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  entitlements: (query?: { pageNo?: number; pageSize?: number }) => {
    const params = new URLSearchParams();
    if (query?.pageNo) params.set("pageNo", String(query.pageNo));
    if (query?.pageSize) params.set("pageSize", String(query.pageSize));
    const suffix = params.toString() ? `?${params.toString()}` : "";
    return apiFetch<PageResult<EntitlementItem>>(`/app/entitlements${suffix}`);
  },
  mockApproveBinding: () =>
    apiFetch<SubmitBindingData>("/app/_mock/approve-binding", { method: "POST" }),
  mockReset: () => apiFetch<{ reset: boolean }>("/app/_mock/reset", { method: "POST" }),
};

export { isMockToolsEnabled };

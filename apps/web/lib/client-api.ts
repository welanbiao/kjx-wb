import { apiFetch } from "@/lib/api";
import type {
  AccountProfile,
  BindingStatusData,
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
} from "@/lib/types";

function idemKey(): string {
  return crypto.randomUUID().replace(/-/g, "").slice(0, 32);
}

export const clientApi = {
  csrf: () => apiFetch<{ csrfToken: string }>("/app/auth/csrf"),
  sendCode: (body: { phone: string; scene: "register" | "login" | "reset_pwd" | "cancel"; captchaId: string; captchaData: Record<string, unknown> }) =>
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
  login: (body: { phone: string; password: string }) =>
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
  resetPassword: (body: { phone: string; code: string; newPassword: string }) =>
    apiFetch<Record<string, never>>("/app/auth/reset-password", {
      method: "POST",
      body: JSON.stringify(body),
      idempotencyKey: idemKey(),
    }),
  profile: () => apiFetch<AccountProfile>("/app/account/profile"),
  updateProfile: (body: { nickname?: string; avatar?: string }) =>
    apiFetch<AccountProfile>("/app/account/profile", { method: "PUT", body: JSON.stringify(body) }),
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
  works: (query?: { workType?: string; unlocked?: boolean }) => {
    const params = new URLSearchParams();
    if (query?.workType) params.set("workType", query.workType);
    if (typeof query?.unlocked === "boolean") params.set("unlocked", String(query.unlocked));
    const suffix = params.toString() ? `?${params.toString()}` : "";
    return apiFetch<PageResult<WorkSummary>>(`/app/works${suffix}`);
  },
  work: (workCode: string) => apiFetch<WorkDetail>(`/app/works/${workCode}`),
  unlock: (workCode: string) =>
    apiFetch<UnlockResult>(`/app/works/${workCode}/unlock`, {
      method: "POST",
      idempotencyKey: idemKey(),
    }),
  entitlements: () => apiFetch<PageResult<EntitlementItem>>("/app/entitlements"),
  mockApproveBinding: () =>
    apiFetch<SubmitBindingData>("/app/_mock/approve-binding", { method: "POST" }),
  mockReset: () => apiFetch<{ reset: boolean }>("/app/_mock/reset", { method: "POST" }),
};

export function isMockToolsEnabled(): boolean {
  return process.env.NEXT_PUBLIC_ENABLE_MOCK_TOOLS === "true";
}

import { ERROR_MESSAGES } from "@/lib/errors";
import { createMockCaptcha, verifyMockCaptchaTrack } from "@/lib/mock/captcha";
import { badRequest, businessError, forbidden, notFound, ok, unauthorized } from "@/lib/mock/http";
import {
  createBreakupSession,
  handleBreakupMock,
  sessionView,
} from "@/lib/mock/breakup";
import { createDreamPhoneSession, DREAM_CODE, handleDreamPhoneMock } from "@/lib/mock/dream-phone";
import {
  daysFromNowIso,
  entryUrlFor,
  getStore,
  MOCK_SMS_CODE,
  nowIso,
  resetStore,
  snapshotMember,
  toBindingStatus,
  toProfile,
  type MockAccount,
  type MockWork,
} from "@/lib/mock/store";
import type { BindingStatus, LoginData, PageResult, WorkSummary } from "@/lib/types";

const PHONE = /^1[3-9]\d{9}$/;
const PASSWORD = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,32}$/;
const SMS = /^\d{6}$/;
const MEMBER_CODE = /^[A-Z]{2,8}-[A-HJ-NP-Z2-9]{8}$/;

type SessionAccount = MockAccount;

function jsonBody(request: Request): Promise<Record<string, unknown>> {
  return request.json().catch(() => ({}));
}

function str(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function num(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function smsKey(phone: string, scene: string): string {
  return `${scene}:${phone}`;
}

function requireAccount(account: SessionAccount | null, requestId: string) {
  if (!account) {
    return unauthorized(requestId);
  }
  return null;
}

function visibleWorks(account: MockAccount | null): MockWork[] {
  const store = getStore();
  const entitled = new Set(
    store.entitlements.filter((item) => item.accountId === account?.id).map((item) => item.workCode),
  );
  return store.works.filter((work) => {
    if (work.status === "PUBLISHED" || work.status === "GRAY") {
      return true;
    }
    return entitled.has(work.workCode);
  });
}

function summarize(work: MockWork, accountId: number | null): WorkSummary {
  const store = getStore();
  const entitlement = store.entitlements.find(
    (item) => item.accountId === accountId && item.workCode === work.workCode,
  );
  return {
    workCode: work.workCode,
    name: work.name,
    workType: work.workType,
    summary: work.summary,
    coverUrl: work.coverUrl,
    category: work.category,
    tags: work.tags,
    status: work.status,
    currentVersion: work.currentVersion,
    accessForm: work.accessForm,
    unlocked: Boolean(entitlement),
    unlockedAt: entitlement?.unlockedAt ?? null,
  };
}

function hashRequest(body: unknown): string {
  return JSON.stringify(body);
}

function idempotencyResult(
  scene: string,
  refId: string,
  key: string | null,
  body: unknown,
  requestId: string,
): Response | "fresh" {
  if (!key) {
    return businessError(requestId, 400, "缺少 X-Idempotency-Key");
  }
  const store = getStore();
  const id = `${scene}:${refId}:${key}`;
  const existing = store.idempotency.get(id);
  const digest = hashRequest(body);
  if (!existing) {
    return "fresh";
  }
  if (existing.hash !== digest) {
    return businessError(requestId, 1090);
  }
  return ok(requestId, existing.body);
}

function rememberIdempotency(scene: string, refId: string, key: string, body: unknown) {
  getStore().idempotency.set(`${scene}:${refId}:${key}`, { hash: hashRequest(body), body, status: 200 });
}

function loginPayload(account: MockAccount): LoginData {
  const binding = toBindingStatus(account.id);
  return {
    expireIn: 2592000,
    account: {
      id: account.id,
      nickname: account.nickname,
      avatar: account.avatar,
      phoneMask: toProfile(account).phoneMask,
    },
    bindStatus: account.bindStatus,
    member: binding.member,
  };
}

function setSession(account: MockAccount): string {
  const token = `mock.${account.id}.${crypto.randomUUID()}`;
  getStore().sessions.set(token, { accountId: account.id, createdAt: Date.now() });
  account.lastLoginAt = nowIso();
  account.loginFailures = 0;
  return token;
}

export function accountFromToken(token: string | undefined): MockAccount | null {
  if (!token) {
    return null;
  }
  const store = getStore();
  const session = store.sessions.get(token);
  if (!session) {
    return null;
  }
  return store.accounts.find((item) => item.id === session.accountId) ?? null;
}

function deviceIdFrom(request: Request): string {
  return request.headers.get("X-Device-Id")?.trim() || "web-unknown";
}

function rememberDevice(account: MockAccount, request: Request) {
  const store = getStore();
  const deviceId = deviceIdFrom(request);
  const existing = store.devices.find((item) => item.accountId === account.id && item.deviceId === deviceId);
  if (existing) {
    existing.lastActiveAt = nowIso();
    return;
  }
  store.devices.push({
    id: store.nextDeviceId,
    accountId: account.id,
    deviceId,
    deviceName: "Web 浏览器",
    platform: "web",
    lastRegion: null,
    lastActiveAt: nowIso(),
  });
  store.nextDeviceId += 1;
}

function listDevices(accountId: number, request: Request) {
  const currentId = deviceIdFrom(request);
  return getStore()
    .devices.filter((item) => item.accountId === accountId)
    .map((item) => ({
      id: item.id,
      deviceId: item.deviceId,
      deviceName: item.deviceName,
      platform: item.platform,
      lastRegion: item.lastRegion,
      lastActiveAt: item.lastActiveAt,
      current: item.deviceId === currentId,
    }));
}

export async function handleMock(
  request: Request,
  pathname: string,
  account: MockAccount | null,
  requestId: string,
): Promise<{ response: Response; sessionToken?: string | null }> {
  const store = getStore();
  const method = request.method.toUpperCase();
  const url = new URL(request.url);
  const idempotencyKey = request.headers.get("X-Idempotency-Key");

  if (pathname === "/app/_mock/reset" && method === "POST") {
    resetStore();
    return { response: ok(requestId, { reset: true }) };
  }

  if (pathname === "/app/_mock/approve-binding" && method === "POST") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const binding = store.bindings.find((item) => item.accountId === account!.id);
    if (!binding || binding.status !== "PENDING_REVIEW") {
      return { response: badRequest(requestId, "当前没有待审核绑定") };
    }
    const member = store.members.find((item) => item.memberCode === binding.memberCode);
    if (!member) {
      return { response: businessError(requestId, 1001) };
    }
    binding.status = "MATCHED";
    member.boundAccountId = account!.id;
    account!.bindStatus = 2;
    return {
      response: ok(requestId, { status: "MATCHED", member: snapshotMember(member) }),
    };
  }

  if (pathname === "/app/auth/captcha" && method === "GET") {
    const type = (url.searchParams.get("type") ?? "SLIDER").trim().toUpperCase();
    if (type && type !== "SLIDER") {
      return { response: badRequest(requestId, "不支持的图形验证码类型") };
    }
    const generated = createMockCaptcha();
    store.captchas.set(generated.record.id, generated.record);
    return { response: ok(requestId, generated.challenge) };
  }

  if (pathname === "/app/auth/captcha/check" && method === "POST") {
    const body = await jsonBody(request);
    const captchaId = str(body.captchaId);
    const record = store.captchas.get(captchaId);
    if (!verifyMockCaptchaTrack(record, body.captchaData)) {
      store.captchas.delete(captchaId);
      return { response: badRequest(requestId, "图形验证码错误或已过期") };
    }
    store.captchas.set(captchaId, { ...record!, passed: true });
    return { response: ok(requestId, { passed: true }) };
  }

  if (pathname === "/app/auth/send-code" && method === "POST") {
    const body = await jsonBody(request);
    const phone = str(body.phone);
    const scene = str(body.scene);
    const captchaId = str(body.captchaId);
    if (!PHONE.test(phone) || !["register", "login", "reset_pwd", "cancel"].includes(scene)) {
      return { response: badRequest(requestId, "手机号或场景无效") };
    }
    const record = store.captchas.get(captchaId);
    store.captchas.delete(captchaId);
    if (record?.passed) {
      // 松手已校验通过
    } else if (!verifyMockCaptchaTrack(record, body.captchaData)) {
      return { response: badRequest(requestId, "图形验证码错误或已过期") };
    }
    store.sms.set(smsKey(phone, scene), {
      code: MOCK_SMS_CODE,
      scene,
      expireAt: Date.now() + 300_000,
    });
    return { response: ok(requestId, { expireIn: 300 }) };
  }

  if (pathname === "/app/auth/register" && method === "POST") {
    const body = await jsonBody(request);
    const phone = str(body.phone);
    const password = str(body.password);
    const code = str(body.code);
    if (body.agreeTerms !== true) {
      return { response: badRequest(requestId, "请同意用户协议") };
    }
    if (!PHONE.test(phone) || !PASSWORD.test(password) || !SMS.test(code)) {
      return { response: badRequest(requestId, "注册信息不完整") };
    }
    const sms = store.sms.get(smsKey(phone, "register"));
    if (!sms || sms.code !== code || sms.expireAt < Date.now()) {
      return { response: businessError(requestId, 1011) };
    }
    if (store.accounts.some((item) => item.phone === phone)) {
      return { response: failConflict(requestId, "手机号已注册") };
    }
    store.sms.delete(smsKey(phone, "register"));
    const created: MockAccount = {
      id: store.nextAccountId,
      phone,
      password,
      nickname: null,
      avatar: null,
      status: 1,
      bindStatus: 0,
      cancelApplyAt: null,
      lastLoginAt: nowIso(),
      createTime: nowIso(),
      loginFailures: 0,
      lockedUntil: null,
    };
    store.nextAccountId += 1;
    store.accounts.push(created);
    rememberDevice(created, request);
    const token = setSession(created);
    return {
      response: ok(requestId, { token, accountId: created.id }),
      sessionToken: token,
    };
  }

  if (pathname === "/app/auth/login" && method === "POST") {
    const body = await jsonBody(request);
    const phone = str(body.phone);
    const password = str(body.password);
    const found = store.accounts.find((item) => item.phone === phone);
    if (!found || found.password !== password) {
      if (found) {
        found.loginFailures += 1;
        if (found.loginFailures >= 5) {
          found.lockedUntil = Date.now() + 15 * 60_000;
          return { response: businessError(requestId, 1013) };
        }
      }
      return { response: badRequest(requestId, "手机号或密码错误") };
    }
    if (found.lockedUntil && found.lockedUntil > Date.now()) {
      return { response: businessError(requestId, 1013) };
    }
    const token = setSession(found);
    rememberDevice(found, request);
    const data = loginPayload(found);
    return {
      response: ok(requestId, { token, ...data }),
      sessionToken: token,
    };
  }

  if (pathname === "/app/auth/login-by-code" && method === "POST") {
    const body = await jsonBody(request);
    const phone = str(body.phone);
    const code = str(body.code);
    const sms = store.sms.get(smsKey(phone, "login"));
    if (!sms || sms.code !== code || sms.expireAt < Date.now()) {
      return { response: businessError(requestId, 1011) };
    }
    const found = store.accounts.find((item) => item.phone === phone);
    if (!found) {
      return { response: badRequest(requestId, "账号不存在") };
    }
    store.sms.delete(smsKey(phone, "login"));
    const token = setSession(found);
    rememberDevice(found, request);
    const data = loginPayload(found);
    return {
      response: ok(requestId, { token, ...data }),
      sessionToken: token,
    };
  }

  if (pathname === "/app/auth/logout" && method === "POST") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    for (const [token, session] of store.sessions) {
      if (session.accountId === account!.id) {
        store.sessions.delete(token);
      }
    }
    return { response: ok(requestId, {}), sessionToken: null };
  }

  if (pathname === "/app/auth/refresh" && method === "POST") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const token = setSession(account!);
    rememberDevice(account!, request);
    const data = loginPayload(account!);
    return { response: ok(requestId, { token, ...data }), sessionToken: token };
  }

  if (pathname === "/app/auth/reset-password" && method === "POST") {
    const body = await jsonBody(request);
    const phone = str(body.phone);
    const code = str(body.code);
    const newPassword = str(body.newPassword);
    if (!PASSWORD.test(newPassword)) {
      return { response: badRequest(requestId, "密码须为 8～32 位字母和数字") };
    }
    const sms = store.sms.get(smsKey(phone, "reset_pwd"));
    if (!sms || sms.code !== code || sms.expireAt < Date.now()) {
      return { response: businessError(requestId, 1011) };
    }
    const found = store.accounts.find((item) => item.phone === phone);
    if (!found) {
      return { response: badRequest(requestId, "账号不存在") };
    }
    found.password = newPassword;
    store.sms.delete(smsKey(phone, "reset_pwd"));
    return { response: ok(requestId, {}) };
  }

  if (pathname === "/app/account/profile" && method === "GET") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    return { response: ok(requestId, toProfile(account!)) };
  }

  if (pathname === "/app/account/profile" && method === "PUT") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const body = await jsonBody(request);
    if (typeof body.nickname === "string") {
      account!.nickname = body.nickname.slice(0, 64);
    }
    if (typeof body.avatar === "string") {
      account!.avatar = body.avatar.slice(0, 512);
    }
    return { response: ok(requestId, toProfile(account!)) };
  }

  if (pathname === "/app/account/ai-settings" && method === "GET") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const saved = store.aiSettings.get(account!.id);
    return {
      response: ok(requestId, {
        configured: Boolean(saved),
        baseUrl: saved?.baseUrl ?? "",
        model: saved?.model ?? "",
      }),
    };
  }

  if (pathname === "/app/account/ai-settings" && method === "POST") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const body = await jsonBody(request);
    const baseUrl = str(body.baseUrl);
    const apiKey = str(body.apiKey);
    const model = str(body.model);
    if (!baseUrl || !apiKey || !model) {
      return { response: badRequest(requestId, "请填写 Base URL、API Key 与 Model") };
    }
    store.aiSettings.set(account!.id, { baseUrl, apiKey, model });
    return {
      response: ok(requestId, { configured: true, baseUrl, model }),
    };
  }

  if (pathname === "/app/account/password" && method === "PUT") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const body = await jsonBody(request);
    const oldPassword = str(body.oldPassword);
    const newPassword = str(body.newPassword);
    if (account!.password !== oldPassword) {
      return { response: badRequest(requestId, "原密码不正确") };
    }
    if (!PASSWORD.test(newPassword)) {
      return { response: badRequest(requestId, "密码须为 8～32 位字母和数字") };
    }
    account!.password = newPassword;
    return { response: ok(requestId, {}) };
  }

  if (pathname === "/app/account/devices" && method === "GET") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    return { response: ok(requestId, listDevices(account!.id, request)) };
  }

  const deviceMatch = pathname.match(/^\/app\/account\/devices\/(\d+)$/);
  if (deviceMatch && method === "DELETE") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const id = Number(deviceMatch[1]);
    const index = store.devices.findIndex((item) => item.id === id && item.accountId === account!.id);
    if (index < 0) {
      return { response: notFound(requestId, "设备不存在") };
    }
    const removed = store.devices[index];
    store.devices.splice(index, 1);
    if (removed.deviceId === deviceIdFrom(request)) {
      for (const [token, session] of store.sessions) {
        if (session.accountId === account!.id) {
          store.sessions.delete(token);
        }
      }
      return { response: ok(requestId, {}), sessionToken: null };
    }
    return { response: ok(requestId, {}) };
  }

  if (pathname === "/app/account/cancel" && method === "POST") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    if (account!.status === 3) {
      return { response: businessError(requestId, 1014) };
    }
    const body = await jsonBody(request);
    const code = str(body.code);
    const sms = store.sms.get(smsKey(account!.phone, "cancel"));
    if (!sms || sms.code !== code || sms.expireAt < Date.now()) {
      return { response: businessError(requestId, 1011) };
    }
    store.sms.delete(smsKey(account!.phone, "cancel"));
    account!.status = 3;
    account!.cancelApplyAt = nowIso();
    return {
      response: ok(requestId, {
        cancelApplyAt: account!.cancelApplyAt,
        effectiveAt: daysFromNowIso(7),
      }),
    };
  }

  if (pathname === "/app/account/cancel/revoke" && method === "POST") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    if (account!.status !== 3) {
      return { response: badRequest(requestId, "当前没有注销申请") };
    }
    account!.status = 1;
    account!.cancelApplyAt = null;
    return { response: ok(requestId, {}) };
  }

  if (pathname === "/app/member/hosts" && method === "GET") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    return { response: ok(requestId, store.hosts) };
  }

  if (pathname === "/app/member/binding" && method === "GET") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    return { response: ok(requestId, toBindingStatus(account!.id)) };
  }

  if (pathname === "/app/member/profile" && method === "GET") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const member = store.members.find((item) => item.boundAccountId === account!.id) ?? null;
    return { response: ok(requestId, member ? snapshotMember(member) : null) };
  }

  if (pathname === "/app/member/binding" && method === "POST") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const body = await jsonBody(request);
    const gate = idempotencyResult("binding", String(account!.id), idempotencyKey, body, requestId);
    if (gate !== "fresh") {
      return { response: gate };
    }
    if (account!.status === 3) {
      return { response: businessError(requestId, 1014) };
    }
    const current = store.bindings.find((item) => item.accountId === account!.id);
    if (current?.status === "MATCHED") {
      return { response: businessError(requestId, 1003) };
    }
    if (current?.status === "PENDING_REVIEW" || current?.status === "UNBINDING") {
      return { response: badRequest(requestId, "当前绑定状态不可重复提交") };
    }
    const fails = store.bindFailures.get(account!.id);
    if (fails?.lockedUntil && fails.lockedUntil > Date.now()) {
      return { response: businessError(requestId, 1004) };
    }
    const douyinId = str(body.douyinId).replace(/^@/, "").toLowerCase();
    const memberCode = str(body.memberCode).toUpperCase();
    const claimMonths = num(body.claimMonths);
    const hostCode = str(body.hostCode) || "KJX";
    if (!douyinId || !MEMBER_CODE.test(memberCode) || !claimMonths) {
      return { response: badRequest(requestId, "请完整填写三要素") };
    }
    const member = store.members.find(
      (item) =>
        item.hostCode === hostCode &&
        item.memberCode === memberCode &&
        item.douyinId === douyinId &&
        item.subscribedMonths === claimMonths,
    );
    if (!member) {
      const nextCount = (fails?.count ?? 0) + 1;
      store.bindFailures.set(account!.id, {
        count: nextCount,
        lockedUntil: nextCount >= 3 ? Date.now() + 30 * 60_000 : null,
      });
      return { response: businessError(requestId, 1001) };
    }
    if (member.boundAccountId && member.boundAccountId !== account!.id) {
      return { response: businessError(requestId, 1002) };
    }
    const status: BindingStatus = store.autoReview ? "MATCHED" : "PENDING_REVIEW";
    const record = {
      accountId: account!.id,
      status,
      submitAt: nowIso(),
      rejectReason: null,
      hostCode,
      douyinId,
      memberCode,
      claimMonths,
    };
    const existingIndex = store.bindings.findIndex((item) => item.accountId === account!.id);
    if (existingIndex >= 0) {
      store.bindings[existingIndex] = record;
    } else {
      store.bindings.push(record);
    }
    if (status === "MATCHED") {
      member.boundAccountId = account!.id;
      account!.bindStatus = 2;
    } else {
      account!.bindStatus = 1;
    }
    const data = {
      status,
      member: status === "MATCHED" ? snapshotMember(member) : null,
    };
    rememberIdempotency("binding", String(account!.id), idempotencyKey!, data);
    return { response: ok(requestId, data) };
  }

  if (pathname === "/app/member/binding/appeal" && method === "POST") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const body = await jsonBody(request);
    if (!str(body.appealType) || !str(body.content)) {
      return { response: badRequest(requestId, "请填写申诉内容") };
    }
    return { response: ok(requestId, { appealId: Date.now(), status: "PENDING" }) };
  }

  if (pathname === "/app/works" && method === "GET") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const unlockedFilter = url.searchParams.get("unlocked");
    const workType = url.searchParams.get("workType");
    const pageNo = Number(url.searchParams.get("pageNo") ?? "1") || 1;
    const pageSize = Number(url.searchParams.get("pageSize") ?? "20") || 20;
    let list = visibleWorks(account).map((work) => summarize(work, account!.id));
    if (workType === "layout" || workType === "game") {
      list = list.filter((item) => item.workType === workType);
    }
    if (unlockedFilter === "true") {
      list = list.filter((item) => item.unlocked);
    } else if (unlockedFilter === "false") {
      list = list.filter((item) => !item.unlocked);
    }
    const page: PageResult<WorkSummary> = {
      list: list.slice((pageNo - 1) * pageSize, pageNo * pageSize),
      total: list.length,
    };
    return { response: ok(requestId, page) };
  }

  const workMatch = pathname.match(/^\/app\/works\/([a-z0-9][a-z0-9-]{1,62})$/);
  if (workMatch && method === "GET") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const workCode = workMatch[1];
    const work = store.works.find((item) => item.workCode === workCode);
    if (!work) {
      return { response: notFound(requestId) };
    }
    const entitlement = store.entitlements.find(
      (item) => item.accountId === account!.id && item.workCode === workCode,
    );
    if (work.status === "UNPUBLISHED" && !entitlement) {
      return { response: businessError(requestId, 1008) };
    }
    if (work.status !== "PUBLISHED" && work.status !== "GRAY" && !entitlement) {
      return { response: notFound(requestId) };
    }
    const summary = summarize(work, account!.id);
    return {
      response: ok(requestId, {
        ...work,
        ...summary,
        entryUrl: entitlement ? entitlement.entryUrl : null,
      }),
    };
  }

  const unlockMatch = pathname.match(/^\/app\/works\/([a-z0-9][a-z0-9-]{1,62})\/unlock$/);
  if (unlockMatch && method === "POST") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const workCode = unlockMatch[1];
    const work = store.works.find((item) => item.workCode === workCode);
    const existing = store.entitlements.find(
      (item) => item.accountId === account!.id && item.workCode === workCode,
    );
    const body = { workCode };
    const workId = work?.workCode ?? workCode;
    const gate = idempotencyResult("unlock", workId, idempotencyKey, body, requestId);
    if (gate !== "fresh") {
      return { response: gate };
    }
    if (existing) {
      const member = store.members.find((item) => item.boundAccountId === account!.id);
      const data = {
        workCode,
        unlockedCount: member?.unlockedCount ?? 1,
        remainCount: member?.remainCount ?? 0,
        entryUrl: existing.entryUrl,
        alreadyUnlocked: true,
      };
      rememberIdempotency("unlock", workId, idempotencyKey!, data);
      return {
        response: okAlready(requestId, data),
      };
    }
    if (account!.status === 3) {
      return { response: businessError(requestId, 1014) };
    }
    if (!work || (work.status !== "PUBLISHED" && work.status !== "GRAY")) {
      return { response: businessError(requestId, 1008) };
    }
    const binding = toBindingStatus(account!.id);
    if (binding.status !== "MATCHED" || !binding.member) {
      return { response: businessError(requestId, 1009) };
    }
    const member = store.members.find((item) => item.boundAccountId === account!.id);
    if (!member) {
      return { response: businessError(requestId, 1009) };
    }
    if (member.status === "NOT_STARTED") {
      return { response: businessError(requestId, 1005, ERROR_MESSAGES[1005]?.replace("{time}", member.startAt ?? ""), { time: member.startAt }) };
    }
    if (member.status === "EXPIRED") {
      return { response: businessError(requestId, 1006) };
    }
    if (member.remainCount <= 0) {
      return { response: businessError(requestId, 1007) };
    }
    member.remainCount -= 1;
    member.unlockedCount += 1;
    const unlockedAt = nowIso();
    const entryUrl = entryUrlFor(workCode);
    store.entitlements.push({
      accountId: account!.id,
      workCode,
      name: work.name,
      coverUrl: work.coverUrl,
      workType: work.workType,
      workStatus: work.status,
      unlockedAt,
      source: "user",
      entryUrl,
    });
    const data = {
      workCode,
      unlockedCount: member.unlockedCount,
      remainCount: member.remainCount,
      entryUrl,
      alreadyUnlocked: false,
    };
    rememberIdempotency("unlock", workId, idempotencyKey!, data);
    return { response: ok(requestId, data) };
  }

  const purchaseMatch = pathname.match(/^\/app\/works\/([a-z0-9][a-z0-9-]{1,62})\/purchase$/);
  if (purchaseMatch && method === "POST") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const workCode = purchaseMatch[1];
    if (workCode !== "breakup-editor" && workCode !== "dream-phone") {
      return { response: badRequest(requestId, "该作品不支持页面购买") };
    }
    const work = store.works.find((item) => item.workCode === workCode);
    const existing = store.entitlements.find(
      (item) => item.accountId === account!.id && item.workCode === workCode,
    );
    const body = { workCode, source: "purchase" };
    const workId = work?.workCode ?? workCode;
    const gate = idempotencyResult("page_purchase", workId, idempotencyKey, body, requestId);
    if (gate !== "fresh") {
      return { response: gate };
    }
    if (existing) {
      const member = store.members.find((item) => item.boundAccountId === account!.id);
      const data = {
        workCode,
        unlockedCount: member?.unlockedCount ?? 0,
        remainCount: member?.remainCount ?? 0,
        entryUrl: existing.entryUrl,
        alreadyUnlocked: true,
        source: "purchase",
      };
      rememberIdempotency("page_purchase", workId, idempotencyKey!, data);
      return { response: okAlready(requestId, data) };
    }
    if (account!.status === 3) {
      return { response: businessError(requestId, 1014) };
    }
    if (!work || (work.status !== "PUBLISHED" && work.status !== "GRAY")) {
      return { response: businessError(requestId, 1008) };
    }
    const member = store.members.find((item) => item.boundAccountId === account!.id);
    const entryUrl = entryUrlFor(workCode);
    store.entitlements.push({
      accountId: account!.id,
      workCode,
      name: work.name,
      coverUrl: work.coverUrl,
      workType: work.workType,
      workStatus: work.status,
      unlockedAt: nowIso(),
      source: "purchase",
      entryUrl,
    });
    const data = {
      workCode,
      unlockedCount: member?.unlockedCount ?? 0,
      remainCount: member?.remainCount ?? 0,
      entryUrl,
      alreadyUnlocked: false,
      source: "purchase",
    };
    rememberIdempotency("page_purchase", workId, idempotencyKey!, data);
    return { response: ok(requestId, data) };
  }

  if (account && pathname.startsWith("/app/sessions")) {
    const breakup = await handleBreakupMock(request, pathname, account.id, requestId);
    if (breakup) {
      return { response: breakup };
    }
    const dream = await handleDreamPhoneMock(request, pathname, account.id, requestId);
    if (dream) {
      return { response: dream };
    }
  }

  if (pathname === "/app/sessions" && method === "POST") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const body = await jsonBody(request);
    const workCode = str(body.workCode);
    const trial = Boolean(body.trial);
    if (workCode !== "breakup-editor" && workCode !== DREAM_CODE) {
      return { response: notFound(requestId, "作品未接入") };
    }
    if (!trial) {
      const entitled = store.entitlements.some(
        (item) => item.accountId === account!.id && item.workCode === workCode,
      );
      if (!entitled) {
        return { response: forbidden(requestId, "entitlement required") };
      }
    }
    const row =
      workCode === DREAM_CODE
        ? createDreamPhoneSession(account!.id, trial)
        : createBreakupSession(account!.id, trial);
    return { response: ok(requestId, sessionView(row, true)) };
  }

  if (pathname === "/app/sessions" && method === "GET") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const workCode = url.searchParams.get("workCode") ?? "";
    const entitled = store.entitlements.some(
      (item) => item.accountId === account!.id && item.workCode === workCode,
    );
    const list = store.runtimeSessions.filter(
      (item) =>
        item.accountId === account!.id &&
        item.workCode === workCode &&
        (entitled || item.trial),
    );
    if (!entitled && list.length === 0 && workCode !== "breakup-editor" && workCode !== DREAM_CODE) {
      return { response: forbidden(requestId, "entitlement required") };
    }
    return { response: ok(requestId, list.map((item) => sessionView(item, false))) };
  }

  if (pathname === "/app/saves" && method === "POST") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const body = await jsonBody(request);
    const sessionId = str(body.sessionId);
    const row = store.runtimeSessions.find(
      (item) => item.sessionId === sessionId && item.accountId === account!.id,
    );
    if (!row) {
      return { response: forbidden(requestId, "session not owned") };
    }
    return {
      response: ok(requestId, {
        sessionId: row.sessionId,
        workCode: row.workCode,
        slot: str(body.slot) || "auto",
        checksum: row.checksum,
        schemaVersion: row.schemaVersion,
        version: row.version,
        stateJson: JSON.stringify(row.payload),
      }),
    };
  }

  if (pathname === "/app/entitlements" && method === "GET") {
    const denied = requireAccount(account, requestId);
    if (denied) {
      return { response: denied };
    }
    const pageNo = Number(url.searchParams.get("pageNo") ?? "1") || 1;
    const pageSize = Number(url.searchParams.get("pageSize") ?? "20") || 20;
    const list = store.entitlements.filter((item) => item.accountId === account!.id);
    return {
      response: ok(requestId, {
        list: list.slice((pageNo - 1) * pageSize, pageNo * pageSize),
        total: list.length,
      }),
    };
  }

  return { response: notFound(requestId, "接口不存在") };
}

function failConflict(requestId: string, msg: string): Response {
  return new Response(JSON.stringify({ code: 409, data: null, msg, requestId }), {
    status: 409,
    headers: { "Content-Type": "application/json; charset=utf-8", "X-Request-Id": requestId },
  });
}

function okAlready(requestId: string, data: unknown): Response {
  return new Response(JSON.stringify({ code: 1010, data, msg: ERROR_MESSAGES[1010], requestId }), {
    status: 200,
    headers: { "Content-Type": "application/json; charset=utf-8", "X-Request-Id": requestId },
  });
}

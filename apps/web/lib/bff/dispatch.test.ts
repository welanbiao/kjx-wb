import { afterEach, describe, expect, it, vi } from "vitest";
import { apiMode, dispatchBff } from "@/lib/bff/dispatch";
import { CSRF_COOKIE, CSRF_HEADER, SESSION_COOKIE } from "@/lib/session";
import { resetStore } from "@/lib/mock/store";

afterEach(() => {
  resetStore();
  delete process.env.KJX_API_MODE;
  delete process.env.KJX_API_ORIGIN;
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

type Jar = Record<string, string>;

function parseSetCookie(response: Response, jar: Jar): Jar {
  const next = { ...jar };
  const headers = "getSetCookie" in response.headers && typeof response.headers.getSetCookie === "function"
    ? response.headers.getSetCookie()
    : [response.headers.get("set-cookie") ?? ""];
  for (const header of headers.filter(Boolean)) {
    for (const part of header.split(/,(?=\s*kjx_)/)) {
      const pair = part.split(";")[0];
      const eq = pair.indexOf("=");
      if (eq > 0) {
        const name = pair.slice(0, eq).trim();
        const value = decodeURIComponent(pair.slice(eq + 1).trim());
        if (part.includes("Max-Age=0") || value === "") {
          delete next[name];
        } else {
          next[name] = value;
        }
      }
    }
  }
  return next;
}

function cookieHeader(jar: Jar): string {
  return Object.entries(jar)
    .map(([name, value]) => `${name}=${encodeURIComponent(value)}`)
    .join("; ");
}

async function call(
  path: string,
  init: RequestInit,
  jar: Jar = {},
): Promise<{ response: Response; json: Record<string, unknown>; jar: Jar }> {
  const headers = new Headers(init.headers);
  if (Object.keys(jar).length > 0) {
    headers.set("Cookie", cookieHeader(jar));
  }
  const response = await dispatchBff(
    new Request(`http://kjx.test${path}`, { ...init, headers }),
  );
  const nextJar = parseSetCookie(response, jar);
  const json = (await response.json()) as Record<string, unknown>;
  return { response, json, jar: nextJar };
}

async function bootstrap(): Promise<Jar> {
  const { jar } = await call("/app/auth/csrf", { method: "GET" });
  expect(jar[CSRF_COOKIE]).toBeTruthy();
  return jar;
}

async function registerPhone(phone: string, jar: Jar) {
  const send = await call(
    "/app/auth/send-code",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        [CSRF_HEADER]: jar[CSRF_COOKIE],
      },
      body: JSON.stringify({
        phone,
        scene: "register",
        captchaId: "mock-captcha",
        captchaData: { passed: true },
      }),
    },
    jar,
  );
  expect(send.json.code).toBe(0);
  const registered = await call(
    "/app/auth/register",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        [CSRF_HEADER]: send.jar[CSRF_COOKIE],
        "X-Idempotency-Key": "reg-key-0001",
      },
      body: JSON.stringify({
        phone,
        password: "Passw0rd1",
        code: "123456",
        agreeTerms: true,
      }),
    },
    send.jar,
  );
  expect(registered.json.code).toBe(0);
  const data = registered.json.data as Record<string, unknown>;
  expect(data.token).toBeUndefined();
  expect(registered.jar[SESSION_COOKIE]).toBeTruthy();
  return registered.jar;
}

function mutatingHeaders(jar: Jar, idem?: string): HeadersInit {
  return {
    "Content-Type": "application/json",
    [CSRF_HEADER]: jar[CSRF_COOKIE],
    ...(idem ? { "X-Idempotency-Key": idem } : {}),
  };
}

describe("BFF session and CSRF", () => {
  it("rejects mutating requests without CSRF", async () => {
    const jar = await bootstrap();
    const { json, response } = await call(
      "/app/auth/login",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ phone: "13900139000", password: "Passw0rd1" }),
      },
      jar,
    );
    expect(response.status).toBe(403);
    expect(json.code).toBe(403);
    expect(json.requestId).toBeTruthy();
  });

  it("strips login token from JSON and stores httpOnly cookie name", async () => {
    const jar = await bootstrap();
    const { json, response, jar: next } = await call(
      "/app/auth/login",
      {
        method: "POST",
        headers: mutatingHeaders(jar),
        body: JSON.stringify({ phone: "13900139000", password: "Passw0rd1" }),
      },
      jar,
    );
    expect(json.code).toBe(0);
    expect((json.data as Record<string, unknown>).token).toBeUndefined();
    expect(next[SESSION_COOKIE]).toBeTruthy();
    const setCookie = response.headers.getSetCookie?.() ?? [response.headers.get("set-cookie") ?? ""];
    expect(setCookie.join("\n")).toMatch(/HttpOnly/i);
  });
});

describe("live BFF safety", () => {
  it("defaults production to live instead of silently enabling Mock", () => {
    vi.stubEnv("NODE_ENV", "production");
    expect(apiMode()).toBe("live");
  });

  it("strips upstream transport headers and clears the session on logout", async () => {
    process.env.KJX_API_MODE = "live";
    process.env.KJX_API_ORIGIN = "http://backend.test";
    const upstreamFetch = vi.fn()
      .mockResolvedValueOnce(new Response(
        JSON.stringify({ code: 0, msg: "ok", data: { token: "upstream-secret", account: { id: 1 } }, requestId: "upstream" }),
        {
          status: 200,
          headers: {
            "Content-Type": "application/json",
            "Content-Length": "999",
            "Content-Encoding": "gzip",
            "Set-Cookie": "upstream_cookie=must-not-pass",
          },
        },
      ))
      .mockResolvedValueOnce(new Response(
        JSON.stringify({ code: 0, msg: "ok", data: {}, requestId: "upstream" }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ));
    vi.stubGlobal("fetch", upstreamFetch);

    const jar = await bootstrap();
    const login = await call(
      "/app/auth/login",
      {
        method: "POST",
        headers: mutatingHeaders(jar),
        body: JSON.stringify({ phone: "13900139000", password: "Passw0rd1" }),
      },
      jar,
    );
    expect((login.json.data as Record<string, unknown>).token).toBeUndefined();
    expect(login.jar[SESSION_COOKIE]).toBe("upstream-secret");
    expect(login.response.headers.has("Content-Length")).toBe(false);
    expect(login.response.headers.has("Content-Encoding")).toBe(false);
    expect(login.response.headers.get("set-cookie")).not.toContain("upstream_cookie");

    const logout = await call(
      "/app/auth/logout",
      { method: "POST", headers: mutatingHeaders(login.jar) },
      login.jar,
    );
    expect(logout.response.status).toBe(200);
    expect(logout.jar[SESSION_COOKIE]).toBeUndefined();
    expect(logout.response.headers.get("set-cookie")).toContain("Max-Age=0");
  });
});

describe("mock member journey", () => {
  it("keeps API member counts and does not derive quota from months", async () => {
    const jar = await bootstrap();
    const session = await registerPhone("13800138001", jar);
    const bind = await call(
      "/app/member/binding",
      {
        method: "POST",
        headers: mutatingHeaders(session, "bind-key-1"),
        body: JSON.stringify({
          hostCode: "KJX",
          douyinId: "zhangsan",
          memberCode: "KJX-A3F9K2B7",
          claimMonths: 15,
        }),
      },
      session,
    );
    expect(bind.json.code).toBe(0);
    expect((bind.json.data as { status: string }).status).toBe("PENDING_REVIEW");

    const approved = await call(
      "/app/_mock/approve-binding",
      { method: "POST", headers: mutatingHeaders(bind.jar) },
      bind.jar,
    );
    expect(approved.json.code).toBe(0);
    const member = (approved.json.data as { member: { availableCount: number; subscribedMonths: number; remainCount: number } }).member;
    expect(member.subscribedMonths).toBe(15);
    expect(member.availableCount).toBe(14);
    expect(member.availableCount).not.toBe(15 * 30);
    expect(member.remainCount).toBe(14);

    const unlocked = await call(
      "/app/works/breakup-editor/unlock",
      { method: "POST", headers: mutatingHeaders(approved.jar, "unlock-1") },
      approved.jar,
    );
    expect(unlocked.json.code).toBe(0);
    const unlockData = unlocked.json.data as { remainCount: number; unlockedCount: number; alreadyUnlocked: boolean };
    expect(unlockData.alreadyUnlocked).toBe(false);
    expect(unlockData.unlockedCount).toBe(1);
    expect(unlockData.remainCount).toBe(13);
  });

  it("returns the five binding statuses from API fields", async () => {
    const jar = await bootstrap();
    const noneSession = await registerPhone("13800138002", jar);
    const none = await call("/app/member/binding", { method: "GET", headers: mutatingHeaders(noneSession) }, noneSession);
    expect((none.json.data as { status: string }).status).toBe("NONE");
    const pending = await call(
      "/app/member/binding",
      {
        method: "POST",
        headers: mutatingHeaders(noneSession, "bind-pending"),
        body: JSON.stringify({
          douyinId: "zhangsan",
          memberCode: "KJX-A3F9K2B7",
          claimMonths: 15,
        }),
      },
      noneSession,
    );
    expect((pending.json.data as { status: string }).status).toBe("PENDING_REVIEW");

    const login = async (phone: string) => {
      const started = await bootstrap();
      const result = await call(
        "/app/auth/login",
        {
          method: "POST",
          headers: mutatingHeaders(started),
          body: JSON.stringify({ phone, password: "Passw0rd1" }),
        },
        started,
      );
      return result.jar;
    };

    const matchedJar = await login("13900139000");
    const matched = await call("/app/member/binding", { method: "GET", headers: mutatingHeaders(matchedJar) }, matchedJar);
    expect((matched.json.data as { status: string }).status).toBe("MATCHED");

    const rejectedJar = await login("13900139001");
    const rejected = await call("/app/member/binding", { method: "GET", headers: mutatingHeaders(rejectedJar) }, rejectedJar);
    expect((rejected.json.data as { status: string }).status).toBe("REJECTED");

    const unbindingJar = await login("13900139002");
    const unbinding = await call("/app/member/binding", { method: "GET", headers: mutatingHeaders(unbindingJar) }, unbindingJar);
    expect((unbinding.json.data as { status: string }).status).toBe("UNBINDING");
  });

  it("allows expired members to keep entitlements but not new unlocks", async () => {
    const jar = await bootstrap();
    const login = await call(
      "/app/auth/login",
      {
        method: "POST",
        headers: mutatingHeaders(jar),
        body: JSON.stringify({ phone: "13900139000", password: "Passw0rd1" }),
      },
      jar,
    );
    const denied = await call(
      "/app/works/breakup-editor/unlock",
      { method: "POST", headers: mutatingHeaders(login.jar, "unlock-expired") },
      login.jar,
    );
    expect(denied.json.code).toBe(1006);
    expect(denied.json.requestId).toBeTruthy();
    expect(String(denied.json.msg)).toMatch(/过期/);

    const already = await call(
      "/app/works/midnight-script/unlock",
      { method: "POST", headers: mutatingHeaders(login.jar, "unlock-owned") },
      login.jar,
    );
    expect(already.json.code).toBe(1010);

    const entitlements = await call("/app/entitlements", { method: "GET", headers: mutatingHeaders(login.jar) }, login.jar);
    const list = (entitlements.json.data as { list: Array<{ workCode: string; workStatus: string; entryUrl: string }> }).list;
    expect(list.some((item) => item.workCode === "midnight-script" && item.entryUrl)).toBe(true);
    expect(list.some((item) => item.workCode === "archived-stage" && item.workStatus === "UNPUBLISHED")).toBe(true);
  });

  it("returns unified mismatch code 1001 without leaking which field failed", async () => {
    const jar = await bootstrap();
    const session = await registerPhone("13800138003", jar);
    const result = await call(
      "/app/member/binding",
      {
        method: "POST",
        headers: mutatingHeaders(session, "bind-bad"),
        body: JSON.stringify({
          douyinId: "not-zhangsan",
          memberCode: "KJX-A3F9K2B7",
          claimMonths: 15,
        }),
      },
      session,
    );
    expect(result.json.code).toBe(1001);
    expect(String(result.json.msg)).not.toMatch(/抖音|月数|编码错/);
  });
});

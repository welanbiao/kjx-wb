import { CSRF_COOKIE, CSRF_HEADER, DEVICE_COOKIE, SESSION_COOKIE } from "@/lib/session";
import { createCsrfToken, csrfTokensMatch } from "@/lib/csrf";
import { applyCookies, parseCookieHeader, serializeCookie } from "@/lib/bff/cookies";
import { fail, newRequestId, ok } from "@/lib/bff/envelope";
import { stripUpstreamHeaders, upstreamContentType } from "@/lib/bff/headers";
import { shouldStripToken, takeAuthToken } from "@/lib/bff/strip-token";
import { accountFromToken, handleMock } from "@/lib/mock/handlers";

const MUTATING = new Set(["POST", "PUT", "PATCH", "DELETE"]);
const LOGOUT_PATH = "/app/auth/logout";

export function apiMode(): "mock" | "live" {
  const configured = process.env.KJX_API_MODE;
  if (configured === "mock" || configured === "live") {
    return configured;
  }
  return process.env.NODE_ENV === "production" ? "live" : "mock";
}

function ensureCsrfAndDevice(existing: ReturnType<typeof parseCookieHeader>): {
  csrf: string;
  device: string;
  setCookies: string[];
} {
  const setCookies: string[] = [];
  let csrf = existing[CSRF_COOKIE];
  if (!csrf) {
    csrf = createCsrfToken();
    setCookies.push(serializeCookie(CSRF_COOKIE, csrf, { httpOnly: false, maxAge: 60 * 60 * 24 * 7 }));
  }
  let device = existing[DEVICE_COOKIE];
  if (!device) {
    device = `web-${createCsrfToken().slice(0, 16)}`;
    setCookies.push(serializeCookie(DEVICE_COOKIE, device, { httpOnly: false, maxAge: 60 * 60 * 24 * 365 }));
  }
  return { csrf, device, setCookies };
}

function attachSessionCookie(setCookies: string[], token: string | null | undefined) {
  if (token === undefined) {
    return;
  }
  if (token === null) {
    setCookies.push(serializeCookie(SESSION_COOKIE, "", { httpOnly: true, clear: true }));
    return;
  }
  setCookies.push(serializeCookie(SESSION_COOKIE, token, { httpOnly: true, maxAge: 60 * 60 * 24 * 30 }));
}

async function stripAuthResponse(pathname: string, response: Response, setCookies: string[]): Promise<Response> {
  if (!shouldStripToken(pathname)) {
    return applyCookies(response, setCookies);
  }
  const raw = await response.text();
  let parsed: unknown = null;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return applyCookies(response, setCookies);
  }
  const { envelope, token } = takeAuthToken(parsed);
  if (token) {
    attachSessionCookie(setCookies, token);
  }
  const headers = new Headers(response.headers);
  headers.set("Content-Type", "application/json; charset=utf-8");
  const next = new Response(JSON.stringify(envelope), { status: response.status, headers });
  return applyCookies(next, setCookies);
}

export async function dispatchBff(request: Request): Promise<Response> {
  const url = new URL(request.url);
  const pathname = url.pathname;
  const requestId = newRequestId(request.headers.get("X-Request-Id"));
  const cookies = parseCookieHeader(request.headers.get("cookie") ?? request.headers.get("Cookie"));
  const extras = ensureCsrfAndDevice(cookies);

  if (pathname === "/app/auth/csrf" && request.method === "GET") {
    return applyCookies(ok(requestId, { csrfToken: extras.csrf }), extras.setCookies);
  }

  if (MUTATING.has(request.method.toUpperCase())) {
    const header = request.headers.get(CSRF_HEADER);
    if (!csrfTokensMatch(header, extras.csrf)) {
      return applyCookies(fail(requestId, 403, "CSRF 校验失败", 403), extras.setCookies);
    }
  }

  if (apiMode() === "mock") {
    const account = accountFromToken(cookies[SESSION_COOKIE]);
    const result = await handleMock(request, pathname, account, requestId);
    if (result.sessionToken !== undefined && !shouldStripToken(pathname)) {
      attachSessionCookie(extras.setCookies, result.sessionToken);
    }
    return stripAuthResponse(pathname, result.response, extras.setCookies);
  }

  return proxyLive(request, pathname, cookies[SESSION_COOKIE], extras, requestId);
}

async function proxyLive(
  request: Request,
  pathname: string,
  sessionToken: string | undefined,
  extras: { csrf: string; device: string; setCookies: string[] },
  requestId: string,
): Promise<Response> {
  const origin = process.env.KJX_API_ORIGIN?.replace(/\/$/, "");
  if (!origin) {
    return applyCookies(fail(requestId, 500, "未配置 KJX_API_ORIGIN", 500), extras.setCookies);
  }
  const url = new URL(request.url);
  const target = `${origin}${pathname}${url.search}`;
  const headers = new Headers();
  headers.set("Accept", "application/json");
  headers.set("Content-Type", upstreamContentType(request.headers.get("Content-Type"), request.method));
  headers.set("X-Request-Id", requestId);
  headers.set("X-Client-Version", process.env.NEXT_PUBLIC_APP_VERSION ?? "0.0.0");
  headers.set("X-Device-Id", extras.device);
  const idem = request.headers.get("X-Idempotency-Key");
  if (idem) {
    headers.set("X-Idempotency-Key", idem);
  }
  if (sessionToken) {
    headers.set("Authorization", `Bearer ${sessionToken}`);
  }
  const init: RequestInit = {
    method: request.method,
    headers,
    cache: "no-store",
  };
  if (request.method !== "GET" && request.method !== "HEAD") {
    init.body = await request.arrayBuffer();
  }
  let upstream: Response;
  try {
    upstream = await fetch(target, init);
  } catch {
    return applyCookies(fail(requestId, 502, "上游服务暂不可用", 502), extras.setCookies);
  }
  if (pathname === LOGOUT_PATH && upstream.ok) {
    attachSessionCookie(extras.setCookies, null);
  }
  const upstreamHeaders = stripUpstreamHeaders(upstream.headers, requestId);
  const contentType = upstream.headers.get("Content-Type") ?? "";
  if (!contentType.includes("json")) {
    const body = await upstream.arrayBuffer();
    return applyCookies(
      new Response(body, {
        status: upstream.status,
        headers: upstreamHeaders,
      }),
      extras.setCookies,
    );
  }
  const safeUpstream = new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: upstreamHeaders,
  });
  return stripAuthResponse(pathname, safeUpstream, extras.setCookies);
}

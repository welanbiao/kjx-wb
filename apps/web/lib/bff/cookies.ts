import { CSRF_COOKIE, DEVICE_COOKIE, SESSION_COOKIE } from "@/lib/session";

export type CookieBag = {
  [SESSION_COOKIE]?: string;
  [CSRF_COOKIE]?: string;
  [DEVICE_COOKIE]?: string;
};

export function parseCookieHeader(header: string | null): CookieBag {
  const bag: CookieBag = {};
  if (!header) {
    return bag;
  }
  for (const part of header.split(";")) {
    const trimmed = part.trim();
    const eq = trimmed.indexOf("=");
    if (eq <= 0) {
      continue;
    }
    const name = trimmed.slice(0, eq).trim();
    const value = decodeURIComponent(trimmed.slice(eq + 1).trim());
    if (name === SESSION_COOKIE || name === CSRF_COOKIE || name === DEVICE_COOKIE) {
      bag[name] = value;
    }
  }
  return bag;
}

export function serializeCookie(
  name: string,
  value: string,
  options: { httpOnly?: boolean; maxAge?: number; clear?: boolean } = {},
): string {
  const pieces = [
    `${name}=${options.clear ? "" : encodeURIComponent(value)}`,
    "Path=/",
    "SameSite=Lax",
  ];
  if (options.httpOnly) {
    pieces.push("HttpOnly");
  }
  if (process.env.NODE_ENV === "production") {
    pieces.push("Secure");
  }
  if (options.clear) {
    pieces.push("Max-Age=0");
  } else if (typeof options.maxAge === "number") {
    pieces.push(`Max-Age=${options.maxAge}`);
  }
  return pieces.join("; ");
}

export function applyCookies(response: Response, setCookies: string[]): Response {
  if (setCookies.length === 0) {
    return response;
  }
  const headers = new Headers(response.headers);
  for (const cookie of setCookies) {
    headers.append("Set-Cookie", cookie);
  }
  return new Response(response.body, { status: response.status, headers });
}

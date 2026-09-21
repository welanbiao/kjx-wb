import type { ApiEnvelope } from "@/lib/types";

export function newRequestId(incoming?: string | null): string {
  if (incoming && incoming.trim().length > 0 && incoming.length <= 64) {
    return incoming.trim();
  }
  return crypto.randomUUID().replace(/-/g, "").slice(0, 26).toUpperCase();
}

export function jsonEnvelope<T>(
  body: ApiEnvelope<T>,
  init?: { status?: number; headers?: HeadersInit },
): Response {
  const headers = new Headers(init?.headers);
  headers.set("Content-Type", "application/json; charset=utf-8");
  headers.set("Cache-Control", "no-store");
  headers.set("X-Request-Id", body.requestId);
  return new Response(JSON.stringify(body), {
    status: init?.status ?? (body.code === 0 || body.code === 1010 ? 200 : 400),
    headers,
  });
}

export function ok<T>(requestId: string, data: T, msg = ""): Response {
  return jsonEnvelope({ code: 0, data, msg, requestId }, { status: 200 });
}

export function fail(
  requestId: string,
  code: number,
  msg: string,
  httpStatus: number,
  data: unknown = null,
): Response {
  return jsonEnvelope({ code, data, msg, requestId }, { status: httpStatus });
}

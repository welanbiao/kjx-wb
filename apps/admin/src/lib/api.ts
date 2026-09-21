/**
 * 管理端 API 客户端占位。
 *
 * 契约见 docs/13 §4：
 * - 响应信封 { code, data, msg, requestId }
 * - 路径只使用 /admin-api/**（含 /admin-api/kjx/self/**）
 * - 本骨架不伪造 RuoYi 登录态，也不缓存 token
 */

export type ApiEnvelope<T> = {
  code: number;
  data: T;
  msg: string;
  requestId: string;
};

export const ADMIN_API_PREFIX = "/admin-api";

export function getApiBaseUrl(): string {
  return import.meta.env?.VITE_API_BASE_URL?.replace(/\/$/, "") ?? "";
}

export function buildHeaders(
  init?: HeadersInit,
  options?: { idempotencyKey?: string },
): Headers {
  const headers = new Headers(init);
  headers.set("Accept", "application/json");
  headers.set("X-Client-Version", import.meta.env?.VITE_APP_VERSION ?? "0.0.0");
  headers.set("X-Request-Id", crypto.randomUUID());
  if (options?.idempotencyKey) {
    headers.set("X-Idempotency-Key", options.idempotencyKey);
  }
  return headers;
}

export async function apiFetch<T>(
  path: string,
  init?: RequestInit & { idempotencyKey?: string },
): Promise<ApiEnvelope<T>> {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  const url = `${getApiBaseUrl()}${normalized}`;
  const { idempotencyKey, headers, ...rest } = init ?? {};
  const response = await fetch(url, {
    ...rest,
    credentials: "include",
    headers: buildHeaders(headers, { idempotencyKey }),
  });
  return (await response.json()) as ApiEnvelope<T>;
}

export async function pingBackendHealth(): Promise<{
  ok: boolean;
  status: number;
  body: string;
}> {
  const base = getApiBaseUrl();
  const response = await fetch(`${base}/actuator/health`, {
    credentials: "include",
    cache: "no-store",
  });
  return {
    ok: response.ok,
    status: response.status,
    body: await response.text(),
  };
}

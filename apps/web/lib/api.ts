import { readCsrfFromDocument } from "@/lib/csrf";
import { readDeviceId } from "@/lib/device";
import { ALREADY_UNLOCKED, ApiError } from "@/lib/errors";
import { CSRF_HEADER } from "@/lib/session";
import type { ApiEnvelope } from "@/lib/types";

export const APP_API_PREFIX = "/app";

export function getApiBaseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ?? "";
}

export function getBffBaseUrl(): string {
  return "";
}

export function buildHeaders(
  init?: HeadersInit,
  options?: { idempotencyKey?: string; csrf?: string },
): Headers {
  const headers = new Headers(init);
  headers.set("Accept", "application/json");
  headers.set("X-Client-Version", process.env.NEXT_PUBLIC_APP_VERSION ?? "0.0.0");
  headers.set("X-Device-Id", readDeviceId());
  headers.set("X-Request-Id", crypto.randomUUID());
  if (options?.idempotencyKey) {
    headers.set("X-Idempotency-Key", options.idempotencyKey);
  }
  const csrf = options?.csrf ?? readCsrfFromDocument();
  if (csrf) {
    headers.set(CSRF_HEADER, csrf);
  }
  return headers;
}

async function ensureCsrf(): Promise<string> {
  const existing = readCsrfFromDocument();
  if (existing) {
    return existing;
  }
  await fetch(`${getBffBaseUrl()}/app/auth/csrf`, { credentials: "include", cache: "no-store" });
  return readCsrfFromDocument();
}

export async function apiFetch<T>(
  path: string,
  init?: RequestInit & { idempotencyKey?: string },
): Promise<ApiEnvelope<T>> {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  const csrf = await ensureCsrf();
  const { idempotencyKey, headers, ...rest } = init ?? {};
  const response = await fetch(`${getBffBaseUrl()}${normalized}`, {
    ...rest,
    credentials: "include",
    headers: buildHeaders(headers, { idempotencyKey, csrf }),
  });
  let payload: ApiEnvelope<T> | null = null;
  try {
    payload = (await response.json()) as ApiEnvelope<T>;
  } catch {
    throw new ApiError({
      code: response.status || 500,
      msg: "响应不是 JSON",
      requestId: response.headers.get("X-Request-Id") ?? "unknown",
      httpStatus: response.status,
    });
  }
  if (payload.code !== 0 && payload.code !== ALREADY_UNLOCKED) {
    throw new ApiError({
      code: payload.code,
      msg: payload.msg,
      requestId: payload.requestId,
      httpStatus: response.status,
      data: payload.data,
    });
  }
  return payload;
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

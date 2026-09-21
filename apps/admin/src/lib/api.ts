/**
 * 管理端 API 客户端。
 *
 * 契约见 docs/13 §4：
 * - 响应信封 { code, data, msg, requestId }
 * - 路径只使用 /admin-api/**（含 /admin-api/kjx/self/**）
 * - Authorization 仅在已登录会话存在时附加，从不伪造
 */

import { isMockApi } from "./env";
import { ApiError } from "./errors";
import { dispatchMock } from "../mock/handlers";
import { getAccessToken } from "./session";
import type { ApiEnvelope } from "./types";

export type { ApiEnvelope } from "./types";

export const ADMIN_API_PREFIX = "/admin-api";

export function getApiBaseUrl(): string {
  return import.meta.env?.VITE_API_BASE_URL?.replace(/\/$/, "") ?? "";
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

export function buildHeaders(
  init?: HeadersInit,
  options?: { idempotencyKey?: string; token?: string | null; requestId?: string },
): Headers {
  const headers = new Headers(init);
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  headers.set("X-Client-Version", import.meta.env?.VITE_APP_VERSION ?? "0.0.0");
  headers.set("X-Request-Id", options?.requestId ?? crypto.randomUUID());
  if (options?.idempotencyKey) {
    headers.set("X-Idempotency-Key", options.idempotencyKey);
  }
  const token = options?.token === undefined ? getAccessToken() : options.token;
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  return headers;
}

export type ApiFetchInit = RequestInit & {
  idempotencyKey?: string;
  token?: string | null;
  requestId?: string;
};

async function parseEnvelope<T>(response: Response, fallbackRequestId: string): Promise<ApiEnvelope<T>> {
  let payload: ApiEnvelope<T> | null = null;
  try {
    payload = (await response.json()) as ApiEnvelope<T>;
  } catch {
    throw new ApiError({
      code: response.status || 500,
      msg: "响应不是 JSON",
      requestId: response.headers.get("X-Request-Id") ?? fallbackRequestId,
      httpStatus: response.status,
    });
  }
  return payload;
}

function assertOk<T>(payload: ApiEnvelope<T>, httpStatus: number): ApiEnvelope<T> {
  if (payload.code !== 0) {
    throw new ApiError({
      code: payload.code,
      msg: payload.msg,
      requestId: payload.requestId,
      httpStatus,
      data: payload.data,
    });
  }
  return payload;
}

export async function apiFetchRaw<T>(
  path: string,
  init?: ApiFetchInit,
): Promise<{ envelope: ApiEnvelope<T>; httpStatus: number }> {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  const { idempotencyKey, token, requestId, headers, ...rest } = init ?? {};
  const built = buildHeaders(headers, { idempotencyKey, token, requestId });
  const sentRequestId = built.get("X-Request-Id") ?? crypto.randomUUID();

  if (isMockApi() && normalized.startsWith(ADMIN_API_PREFIX)) {
    const result = await dispatchMock(normalized, { ...rest, headers: built });
    if (result.kind === "blob") {
      throw new ApiError({
        code: 400,
        msg: "请使用下载接口处理导出",
        requestId: result.requestId,
        httpStatus: 400,
      });
    }
    return { envelope: result.envelope as ApiEnvelope<T>, httpStatus: result.httpStatus };
  }

  const url = `${getApiBaseUrl()}${normalized}`;
  const response = await fetch(url, {
    ...rest,
    credentials: "include",
    headers: built,
  });
  const payload = await parseEnvelope<T>(response, sentRequestId);
  return { envelope: payload, httpStatus: response.status };
}

export async function apiFetch<T>(path: string, init?: ApiFetchInit): Promise<ApiEnvelope<T>> {
  const { envelope, httpStatus } = await apiFetchRaw<T>(path, init);
  if (httpStatus === 401 || envelope.code === 401) {
    throw new ApiError({
      code: envelope.code || 401,
      msg: envelope.msg || "未登录或会话已失效",
      requestId: envelope.requestId,
      httpStatus: 401,
      data: envelope.data,
    });
  }
  return assertOk(envelope, httpStatus);
}

export async function apiDownload(path: string, init?: ApiFetchInit): Promise<{ blob: Blob; requestId: string }> {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  const { idempotencyKey, token, requestId, headers, ...rest } = init ?? {};
  const built = buildHeaders(headers, { idempotencyKey, token, requestId });
  built.set("Accept", "*/*");
  const sentRequestId = built.get("X-Request-Id") ?? crypto.randomUUID();

  if (isMockApi() && normalized.startsWith(ADMIN_API_PREFIX)) {
    const result = await dispatchMock(normalized, { ...rest, headers: built });
    if (result.kind === "blob") {
      return { blob: result.blob, requestId: result.requestId };
    }
    assertOk(result.envelope, result.httpStatus);
    throw new ApiError({
      code: 400,
      msg: "导出响应不是文件",
      requestId: result.envelope.requestId,
      httpStatus: 400,
    });
  }

  const response = await fetch(`${getApiBaseUrl()}${normalized}`, {
    ...rest,
    credentials: "include",
    headers: built,
  });
  const responseId = response.headers.get("X-Request-Id") ?? sentRequestId;
  if (!response.ok) {
    const payload = await parseEnvelope<unknown>(response, responseId).catch(() => null);
    throw new ApiError({
      code: payload?.code ?? response.status,
      msg: payload?.msg ?? "导出失败",
      requestId: payload?.requestId ?? responseId,
      httpStatus: response.status,
    });
  }
  return { blob: await response.blob(), requestId: responseId };
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

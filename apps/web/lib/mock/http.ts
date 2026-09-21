import { ERROR_HTTP_STATUS, ERROR_MESSAGES } from "@/lib/errors";
import { fail, ok } from "@/lib/bff/envelope";

export function businessError(requestId: string, code: number, extraMsg?: string, data?: unknown): Response {
  const status = ERROR_HTTP_STATUS[code] ?? 400;
  const msg = extraMsg ?? ERROR_MESSAGES[code] ?? "请求失败";
  return fail(requestId, code, msg, status, data ?? null);
}

export function unauthorized(requestId: string): Response {
  return fail(requestId, 401, "未登录或会话已失效", 401);
}

export function notFound(requestId: string, msg = "资源不存在"): Response {
  return fail(requestId, 404, msg, 404);
}

export function badRequest(requestId: string, msg: string): Response {
  return fail(requestId, 400, msg, 400);
}

export { ok };

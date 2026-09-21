import { ApiError } from "../lib/errors";
import type { AdminSession, RoleKey } from "../lib/types";

/**
 * RuoYi-Vue-Plus login is not in contracts/openapi.
 * This adapter maps typical `/admin-api/auth/login` + `/system/user/getInfo`
 * without inventing kjx business fields.
 */
export type RuoyiLoginBody = {
  username: string;
  password: string;
  code?: string;
  uuid?: string;
  clientId?: string;
  grantType?: string;
  tenantId?: string;
};

type LooseEnvelope = {
  code?: number;
  msg?: string;
  message?: string;
  requestId?: string;
  data?: unknown;
  token?: string;
  access_token?: string;
};

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : null;
}

function pickToken(data: unknown): string | null {
  const record = asRecord(data);
  if (!record) {
    return typeof data === "string" ? data : null;
  }
  const token = record.access_token ?? record.accessToken ?? record.token;
  return typeof token === "string" && token ? token : null;
}

function pickRoles(data: unknown): RoleKey[] {
  const record = asRecord(data);
  if (!record) {
    return [];
  }
  const raw = record.roles ?? record.roleKeys ?? record.rolePermission;
  const list = Array.isArray(raw)
    ? raw
    : raw && typeof raw === "object"
      ? Object.keys(raw as Record<string, unknown>)
      : [];
  const roles: RoleKey[] = [];
  for (const item of list) {
    const key = typeof item === "string" ? item : asRecord(item)?.roleKey;
    if (key === "HOST" || key === "OPERATOR" || key === "superadmin" || key === "PUBLISHER" || key === "FINANCE") {
      roles.push(key);
    }
  }
  const user = asRecord(record.user);
  if (user?.admin === true && !roles.includes("superadmin")) {
    roles.push("superadmin");
  }
  return roles;
}

export function mapRuoyiLogin(payload: LooseEnvelope, requestId: string): string {
  const ok = payload.code === 0 || payload.code === 200 || payload.code === undefined;
  if (!ok) {
    throw new ApiError({
      code: payload.code ?? 401,
      msg: payload.msg ?? payload.message ?? "登录失败",
      requestId,
      httpStatus: 401,
    });
  }
  const token = pickToken(payload.data) ?? payload.token ?? payload.access_token;
  if (!token) {
    throw new ApiError({
      code: 401,
      msg: "登录响应缺少 token",
      requestId,
      httpStatus: 401,
    });
  }
  return token;
}

export function mapRuoyiProfile(payload: LooseEnvelope, token: string, requestId: string): AdminSession {
  const ok = payload.code === 0 || payload.code === 200 || payload.code === undefined;
  if (!ok) {
    throw new ApiError({
      code: payload.code ?? 401,
      msg: payload.msg ?? payload.message ?? "读取用户失败",
      requestId,
      httpStatus: 401,
    });
  }
  const data = payload.data ?? payload;
  const record = asRecord(data) ?? {};
  const user = asRecord(record.user) ?? record;
  const username = String(user.userName ?? user.username ?? user.nickName ?? "admin");
  const userId = Number(user.userId ?? user.id ?? 0);
  const deptId = user.deptId == null ? null : Number(user.deptId);
  const roles = pickRoles(data);
  return {
    token,
    username,
    userId,
    deptId: Number.isFinite(deptId) ? deptId : null,
    roles: roles.length ? roles : ["OPERATOR"],
  };
}

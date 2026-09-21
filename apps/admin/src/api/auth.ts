import { mapRuoyiLogin, mapRuoyiProfile } from "./session-adapter";
import { isMockApi } from "../lib/env";
import { apiFetch, apiFetchRaw, newIdempotencyKey } from "../lib/api";
import { clearSession, setSession } from "../lib/session";
import type { AdminSession } from "../lib/types";

export async function loginAdmin(username: string, password: string): Promise<AdminSession> {
  if (isMockApi()) {
    const payload = await apiFetch<AdminSession>("/admin-api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    setSession(payload.data);
    return payload.data;
  }

  const requestId = crypto.randomUUID();
  const login = await apiFetchRaw<unknown>("/admin-api/auth/login", {
    method: "POST",
    requestId,
    idempotencyKey: newIdempotencyKey(),
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      username,
      password,
      grantType: "password",
      tenantId: "000000",
    }),
    token: null,
  });
  const token = mapRuoyiLogin(login.envelope, login.envelope.requestId || requestId);
  const info = await apiFetchRaw<unknown>("/admin-api/system/user/getInfo", {
    method: "GET",
    token,
  });
  const session = mapRuoyiProfile(info.envelope, token, info.envelope.requestId || requestId);
  setSession(session);
  return session;
}

export async function logoutAdmin(): Promise<void> {
  try {
    await apiFetch("/admin-api/auth/logout", { method: "POST" });
  } catch {
    // still clear local session
  } finally {
    clearSession();
  }
}

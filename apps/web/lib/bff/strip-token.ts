const AUTH_PATHS = new Set(["/app/auth/login", "/app/auth/login-by-code", "/app/auth/register", "/app/auth/refresh"]);

export function shouldStripToken(pathname: string): boolean {
  return AUTH_PATHS.has(pathname);
}

export function stripTokenFromPayload(payload: unknown): unknown {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return payload;
  }
  const record = payload as Record<string, unknown>;
  if (!("data" in record) || !record.data || typeof record.data !== "object" || Array.isArray(record.data)) {
    return payload;
  }
  const data = { ...(record.data as Record<string, unknown>) };
  const token = typeof data.token === "string" ? data.token : null;
  delete data.token;
  return { data, token, envelope: { ...record, data } };
}

export type StrippedAuth = {
  envelope: Record<string, unknown>;
  token: string | null;
};

export function takeAuthToken(payload: unknown): StrippedAuth {
  const stripped = stripTokenFromPayload(payload);
  if (!stripped || typeof stripped !== "object" || !("envelope" in stripped)) {
    return { envelope: (payload as Record<string, unknown>) ?? {}, token: null };
  }
  const result = stripped as { envelope: Record<string, unknown>; token: string | null };
  return { envelope: result.envelope, token: result.token };
}

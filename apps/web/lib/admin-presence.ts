/** Same key as apps/admin session storage. Presence only; the token stays unread. */
export const ADMIN_SESSION_KEY = "kjx.admin.session.v1";

export function hasAdminDeskSession(raw: string | null): boolean {
  if (!raw) {
    return false;
  }
  try {
    const parsed = JSON.parse(raw) as { username?: unknown; roles?: unknown };
    return (
      typeof parsed.username === "string" &&
      parsed.username.length > 0 &&
      Array.isArray(parsed.roles) &&
      parsed.roles.length > 0
    );
  } catch {
    return false;
  }
}

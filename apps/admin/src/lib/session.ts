import { reactive } from "vue";
import type { AdminSession } from "./types";

const STORAGE_KEY = "kjx.admin.session.v1";

const state = reactive<{ session: AdminSession | null }>({
  session: readStoredSession(),
});

function readStoredSession(): AdminSession | null {
  if (typeof sessionStorage === "undefined") {
    return null;
  }
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as AdminSession;
    if (!parsed?.username || !Array.isArray(parsed.roles)) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function getSession(): AdminSession | null {
  return state.session;
}

export function sessionState(): { session: AdminSession | null } {
  return state;
}

export function setSession(session: AdminSession): void {
  state.session = session;
  if (typeof sessionStorage === "undefined") {
    return;
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  state.session = null;
  if (typeof sessionStorage === "undefined") {
    return;
  }
  sessionStorage.removeItem(STORAGE_KEY);
}

export function getAccessToken(): string | null {
  return state.session?.token ?? null;
}

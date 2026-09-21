import { CSRF_COOKIE } from "@/lib/session";

export function readCsrfFromDocument(): string {
  if (typeof document === "undefined") {
    return "";
  }
  const match = document.cookie.split("; ").find((part) => part.startsWith(`${CSRF_COOKIE}=`));
  if (!match) {
    return "";
  }
  return decodeURIComponent(match.slice(CSRF_COOKIE.length + 1));
}

export function createCsrfToken(): string {
  return crypto.randomUUID().replace(/-/g, "");
}

export function csrfTokensMatch(header: string | null, cookie: string | null): boolean {
  if (!header || !cookie) {
    return false;
  }
  return header === cookie && header.length >= 16;
}

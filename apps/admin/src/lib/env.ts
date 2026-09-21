export type ApiMode = "mock" | "live";

/**
 * Explicit mock vs live. Production without VITE_API_MODE is live.
 * Local `.env.example` sets mock so development never silently hits a real backend.
 */
export function getApiMode(): ApiMode {
  const raw = import.meta.env?.VITE_API_MODE?.trim().toLowerCase();
  if (raw === "mock") {
    return "mock";
  }
  if (raw === "live") {
    return "live";
  }
  return import.meta.env?.DEV ? "mock" : "live";
}

export function isMockApi(): boolean {
  return getApiMode() === "mock";
}

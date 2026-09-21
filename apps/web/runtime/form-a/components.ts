export const FORM_A_TYPES = [
  "chat.bubble",
  "chat.image",
  "chat.voice",
  "chat.quote",
  "chat.system",
  "chat.typing",
  "chat.stream-cursor",
  "chat.read-receipt",
  "redpacket-card",
  "transfer-card",
  "music-card",
  "game-invite-card",
  "link-card",
  "moment-card",
  "moment-image-grid",
  "moment-comment",
  "moment-like",
  "photo-grid",
  "photo-viewer",
  "memo-list",
  "memo-item",
  "wallet-balance",
  "wallet-bill-item",
  "order-card",
  "order-timeline",
  "order-detail",
  "music-player",
  "music-list",
  "listen-together",
  "board-gomoku",
  "rps-panel",
  "guess-number",
  "word-guess",
  "dialog",
  "sheet",
  "toast",
  "badge",
  "tabs",
  "list",
  "empty-state",
  "desktop-icon",
  "status-bar",
  "nav-stack",
  "app-frame",
] as const;

const SLOT = /^custom\.slot:[A-Za-z0-9_-]{1,32}$/;

export function isFormAType(type: string): boolean {
  return (FORM_A_TYPES as readonly string[]).includes(type) || SLOT.test(type);
}

export function parseJsonObject(raw: string): Record<string, unknown> {
  try {
    const value = JSON.parse(raw) as unknown;
    return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

export function parseJsonArray(raw: string): unknown[] {
  try {
    const value = JSON.parse(raw) as unknown;
    return Array.isArray(value) ? value : [];
  } catch {
    return [];
  }
}

export function textProp(props: Record<string, unknown> | undefined, key: string, fallback = ""): string {
  const value = props?.[key];
  return typeof value === "string" ? value : fallback;
}

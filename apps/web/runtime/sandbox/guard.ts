export const HOST_METHODS = [
  "ai.complete",
  "state.save",
  "state.load",
  "assets.sign",
  "events.emit",
  "events.schedule",
  "metrics.report",
  "user.profile",
  "tools.invoke",
] as const;

export type HostMethod = (typeof HOST_METHODS)[number];

export const IFRAME_SANDBOX = "allow-scripts allow-forms";

export type SandboxMessage = {
  type?: string;
  nonce?: string;
  method?: string;
  id?: string;
  payload?: unknown;
};

export function isHostMethod(method: string): method is HostMethod {
  return (HOST_METHODS as readonly string[]).includes(method);
}

export function normalizeOrigin(origin: string | null | undefined): string {
  if (!origin || origin === "null") {
    return "null";
  }
  return origin;
}

export function acceptSandboxMessage(input: {
  data: SandboxMessage;
  origin: string;
  expectedNonce: string;
  allowedOrigin: string;
}): { ok: true; method: HostMethod; id: string; payload: unknown } | { ok: false; reason: string } {
  if (input.data?.type !== "kjx-rpc") {
    return { ok: false, reason: "type" };
  }
  if (input.data.nonce !== input.expectedNonce) {
    return { ok: false, reason: "nonce" };
  }
  if (normalizeOrigin(input.origin) !== normalizeOrigin(input.allowedOrigin)) {
    return { ok: false, reason: "origin" };
  }
  const method = input.data.method ?? "";
  if (!isHostMethod(method)) {
    return { ok: false, reason: "method" };
  }
  return { ok: true, method, id: input.data.id ?? "", payload: input.data.payload };
}

const SECRET = /token|authorization|cookie|set-cookie|csrf|secret|api[-_]?key/i;

export function payloadLooksSafeForIframe(payload: unknown): boolean {
  try {
    return !SECRET.test(JSON.stringify(payload));
  } catch {
    return false;
  }
}

export function hostReply(id: string, nonce: string, ok: boolean, payload: unknown) {
  return { type: "kjx-rpc-result", id, nonce, ok, payload };
}

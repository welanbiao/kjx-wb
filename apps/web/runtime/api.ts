import { apiFetch } from "@/lib/api";
import type { HostResult, SandboxBoot, SessionView, StageView } from "@/runtime/types";

function idemKey(): string {
  return crypto.randomUUID().replace(/-/g, "").slice(0, 32);
}

export const runtimeApi = {
  createSession: (workCode: string) =>
    apiFetch<SessionView>("/app/sessions", {
      method: "POST",
      body: JSON.stringify({ workCode }),
      idempotencyKey: idemKey(),
    }),
  listSessions: (workCode: string) =>
    apiFetch<SessionView[]>(`/app/sessions?workCode=${encodeURIComponent(workCode)}`),
  getSession: (sessionId: string, workCode: string) =>
    apiFetch<SessionView>(`/app/sessions/${encodeURIComponent(sessionId)}?workCode=${encodeURIComponent(workCode)}`),
  apply: (sessionId: string, workCode: string, type: string, schemaVersion: string, payload: Record<string, unknown>) =>
    apiFetch<SessionView>(`/app/sessions/${encodeURIComponent(sessionId)}/state?workCode=${encodeURIComponent(workCode)}`, {
      method: "POST",
      body: JSON.stringify({ type, schemaVersion, payloadJson: JSON.stringify(payload) }),
    }),
  stage: (sessionId: string, workCode: string) =>
    apiFetch<StageView>(`/app/sessions/${encodeURIComponent(sessionId)}/stage?workCode=${encodeURIComponent(workCode)}`),
  sandbox: (sessionId: string, workCode: string) =>
    apiFetch<SandboxBoot>(`/app/sessions/${encodeURIComponent(sessionId)}/sandbox?workCode=${encodeURIComponent(workCode)}`),
  save: (sessionId: string, workCode: string, slot = "auto", version?: number) =>
    apiFetch<Record<string, unknown>>("/app/saves", {
      method: "POST",
      body: JSON.stringify({ sessionId, workCode, slot, version }),
    }),
  host: (body: {
    sessionId: string;
    workCode: string;
    nonce: string;
    iframeOrigin: string;
    method: string;
    payloadJson: string;
  }) => apiFetch<HostResult>("/app/runtime/host", { method: "POST", body: JSON.stringify(body) }),
  invokeTool: (body: {
    sessionId: string;
    workCode: string;
    nonce?: string;
    iframeOrigin?: string;
    payloadJson: string;
  }) => apiFetch<HostResult>("/app/tools/invoke", { method: "POST", body: JSON.stringify(body) }),
};

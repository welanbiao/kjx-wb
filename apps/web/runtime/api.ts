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
  createSessionWithTrial: (workCode: string, trial: boolean) =>
    apiFetch<SessionView>("/app/sessions", {
      method: "POST",
      body: JSON.stringify({ workCode, trial }),
      idempotencyKey: idemKey(),
    }),
  breakupView: (sessionId: string) =>
    apiFetch<BreakupView>(`/app/sessions/${encodeURIComponent(sessionId)}/breakup`),
  breakupAction: (sessionId: string, body: Record<string, unknown>) =>
    apiFetch<BreakupView>(`/app/sessions/${encodeURIComponent(sessionId)}/breakup/actions`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  dreamPhoneView: (sessionId: string) =>
    apiFetch<DreamPhoneView>(`/app/sessions/${encodeURIComponent(sessionId)}/dream-phone`),
  dreamPhoneAction: (sessionId: string, body: Record<string, unknown>) =>
    apiFetch<DreamPhoneView>(`/app/sessions/${encodeURIComponent(sessionId)}/dream-phone/actions`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
};

export type BreakupView = {
  sessionId: string;
  workCode: string;
  trial: boolean;
  coupleLabel: string;
  payload: Record<string, unknown>;
  wall: {
    slides: Array<{
      id: string;
      playable: boolean;
      locked: boolean;
      category: string;
      typeTag: string;
      couple: string;
      gold: string;
      leftPhone: string;
      rightPhone: string;
    }>;
    exampleEyebrow?: string;
    trialBadge?: string;
    lockedLabel?: string;
    gold?: string;
  };
  couple: Record<string, unknown>;
  phoneLife: { threads?: Array<Record<string, unknown>> };
  assets: Record<string, string>;
  css: { fonts: string; app: string; v4: string };
  copy: Record<string, string>;
  replies?: string[];
  ripple?: string;
  worldMoved?: boolean;
};

export type DreamPhoneView = {
  sessionId: string;
  workCode: string;
  trial: boolean;
  playerName: string;
  characterName: string;
  uncovered: string;
  payload: {
    screen?: string;
    playerName?: string;
    characterName?: string;
    currentCharId?: string;
    wallet?: number;
    fontScale?: string;
    chatBg?: string;
    setupDone?: boolean;
    trial?: boolean;
    characters?: Array<{ id: string; name: string; emoji?: string; color?: string; avatar?: string }>;
    chatHistory?: Record<string, Array<{ role?: string; content?: string; text?: string; type?: string; time?: number; amount?: number }>>;
    messages?: Array<{ role?: string; content?: string; text?: string; type?: string }>;
    moments?: Array<{
      id?: string;
      author?: string;
      content?: string;
      charEmoji?: string;
      liked?: boolean;
      likes?: string[];
      comments?: Array<{ name?: string; text?: string }>;
      time?: number;
    }>;
    memos?: Array<{ id?: string; content?: string; day?: string; time?: number }>;
    photos?: Array<{ id?: string; url?: string; desc?: string }>;
    walletLedger?: Array<{ title?: string; amount?: number; time?: number }>;
    charWallets?: Record<string, number>;
    orders?: Array<{ id?: string; app?: string; title?: string; amount?: number; status?: string }>;
    musicTracks?: Array<{ id?: string; title?: string; artist?: string }>;
    gameRecords?: Record<string, { lastResult?: string; plays?: number }>;
    calls?: Array<{ note?: string; time?: number }>;
  };
};

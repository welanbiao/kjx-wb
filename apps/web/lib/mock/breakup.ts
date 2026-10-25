import { readFileSync } from "node:fs";
import { join } from "node:path";
import { badRequest, forbidden, ok } from "@/lib/mock/http";
import { getStore, type MockRuntimeSession } from "@/lib/mock/store";

export const BREAKUP_CODE = "breakup-editor";
export const COUPLE_LABEL = "\u6C5F\u96EA \u00D7 \u5F20\u65F8\u5955";
const DEMO_UNCOVERED = "演示模式暂未覆盖这句改写";
const LOCKED = "敬请期待";

function checksum(payload: Record<string, unknown>): string {
  const raw = JSON.stringify(payload);
  let hash = 0;
  for (let i = 0; i < raw.length; i += 1) {
    hash = (hash * 31 + raw.charCodeAt(i)) | 0;
  }
  return `mock-${Math.abs(hash).toString(16)}`;
}

function cssBundle(): { fonts: string; app: string; v4: string } {
  const dir = join(process.cwd(), "app/play/breakup-editor/vendor");
  const read = (name: string) => {
    try {
      return readFileSync(join(dir, name), "utf8");
    } catch {
      return "";
    }
  };
  return { fonts: read("fonts.css.txt"), app: read("app.css.txt"), v4: read("v4.css.txt") };
}

function initialPayload(trial: boolean): Record<string, unknown> {
  return {
    trial,
    work: BREAKUP_CODE,
    coupleId: "asphyxia",
    coupleLabel: COUPLE_LABEL,
    screen: "screen-title",
    side: "",
    worldline: "original",
    bp: 100,
    lm: 24,
    lf: 70,
    edits: [],
    achievements: [],
    world: { threadPatches: {}, noteHints: [] },
    rewritten: { nodes: [] },
    titles: [],
  };
}

function wall() {
  return {
    exampleEyebrow: "玩法示例",
    trialBadge: "试玩",
    lockedLabel: LOCKED,
    gold: "他曾为她，删掉最好的朋友；最后，为朋友的一句话，删掉了她。",
    slides: [
      {
        id: "asphyxia",
        playable: true,
        locked: false,
        category: "玩法示例",
        typeTag: "回避型依恋 × 焦虑型依恋",
        couple: COUPLE_LABEL,
        gold: "他曾为她，删掉最好的朋友；最后，为朋友的一句话，删掉了她。",
        leftPhone: "wall-phone-complete-asphyxia-left.webp",
        rightPhone: "wall-phone-complete-asphyxia-right.webp",
      },
      {
        id: "growth",
        playable: false,
        locked: true,
        category: "自我成长型",
        typeTag: "敏感自卑 × 过分施舍",
        couple: "江迟 × 温宁",
        gold: "",
        leftPhone: "wall-phone-complete-growth-left.webp",
        rightPhone: "wall-phone-complete-growth-right.webp",
      },
    ],
  };
}

function viewOf(row: MockRuntimeSession) {
  return {
    sessionId: row.sessionId,
    workCode: BREAKUP_CODE,
    trial: row.trial,
    coupleLabel: COUPLE_LABEL,
    payload: row.payload,
    wall: wall(),
    couple: { a: { name: "\u6C5F\u96EA" }, b: { name: "\u5F20\u65F8\u5955" } },
    phoneLife: {
      threads: [
        { id: "juan", name: "娟姐", preview: "你还跟他在一起啊", unread: 1, messages: [] },
        { id: "ma", name: "妈妈", preview: "早点睡", unread: 0, messages: [] },
      ],
    },
    assets: {
      "wall-phone-complete-asphyxia-left.webp":
        "/app/works/breakup-editor/assets?key=wall-phone-complete-asphyxia-left.webp&e=1&s=mock",
      "wall-phone-complete-asphyxia-right.webp":
        "/app/works/breakup-editor/assets?key=wall-phone-complete-asphyxia-right.webp&e=1&s=mock",
      "wallpaper-asphyxia-him-home.webp":
        "/app/works/breakup-editor/assets?key=wallpaper-asphyxia-him-home.webp&e=1&s=mock",
      "wallpaper-dusk.webp": "/app/works/breakup-editor/assets?key=wallpaper-dusk.webp&e=1&s=mock",
    },
    css: cssBundle(),
    copy: {
      demoUncovered: DEMO_UNCOVERED,
      originalSend: "在历史里无法发送新消息。请切换到改写版。",
      originalEdit: "这是已经发生的历史，无法改写。请切换到改写版。",
      locked: LOCKED,
      trialBadge: "试玩",
      exampleEyebrow: "玩法示例",
    },
  };
}

function persist(row: MockRuntimeSession) {
  if (row.trial) {
    row.payload.achievements = [];
  }
  row.checksum = checksum(row.payload);
  row.version += 1;
}

export async function handleBreakupMock(
  request: Request,
  pathname: string,
  accountId: number,
  requestId: string,
): Promise<Response | null> {
  const store = getStore();
  const method = request.method.toUpperCase();

  const breakupGet = pathname.match(/^\/app\/sessions\/([^/]+)\/breakup$/);
  if (breakupGet && method === "GET") {
    const row = store.runtimeSessions.find((item) => item.sessionId === breakupGet[1] && item.accountId === accountId);
    if (!row) {
      return forbidden(requestId, "session not owned");
    }
    return ok(requestId, viewOf(row));
  }

  const breakupPost = pathname.match(/^\/app\/sessions\/([^/]+)\/breakup\/actions$/);
  if (breakupPost && method === "POST") {
    const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
    const row = store.runtimeSessions.find(
      (item) => item.sessionId === breakupPost[1] && item.accountId === accountId,
    );
    if (!row) {
      return forbidden(requestId, "session not owned");
    }
    const type = String(body.type ?? "");
    if (type === "open-couple") {
      if (String(body.coupleId) !== "asphyxia") {
        return badRequest(requestId, LOCKED);
      }
      row.payload.screen = "screen-side";
    } else if (type === "pick-side") {
      row.payload.side = String(body.side ?? "a");
      row.payload.screen = "screen-hook";
    } else if (type === "set-worldline") {
      row.payload.worldline = String(body.worldline) === "rewritten" ? "rewritten" : "original";
    } else if (type === "set-screen") {
      row.payload.screen = String(body.screen ?? row.payload.screen);
    } else if (type === "rewrite") {
      if (row.payload.worldline !== "rewritten") {
        return badRequest(requestId, "这是已经发生的历史，无法改写。请切换到改写版。");
      }
      const msgId = String(body.msgId ?? "");
      const text = String(body.text ?? "");
      if (msgId !== "n7g" && msgId !== "n15g") {
        return badRequest(requestId, DEMO_UNCOVERED);
      }
      const edits = Array.isArray(row.payload.edits) ? row.payload.edits : [];
      edits.push({ msgId, text, trial: row.trial });
      row.payload.edits = edits;
      if (/对不起|抱歉|想你|别走/.test(text)) {
        row.payload.world = {
          threadPatches: { juan: { preview: "你们……和好了？", unread: 1, previewTime: "刚刚" } },
          noteHints: ["未发送的草稿里，有一行字被删了又打回来。"],
        };
      }
    } else if (type === "send") {
      if (row.payload.worldline !== "rewritten") {
        return badRequest(requestId, "在历史里无法发送新消息。请切换到改写版。");
      }
      if (row.trial) {
        return badRequest(requestId, DEMO_UNCOVERED);
      }
    } else if (type === "monitor") {
      row.payload.screen = "screen-monitor";
    } else if (type === "release") {
      row.payload.ended = true;
    }
    persist(row);
    return ok(requestId, viewOf(row));
  }

  return null;
}

export function createBreakupSession(accountId: number, trial: boolean): MockRuntimeSession {
  const store = getStore();
  const existing = store.runtimeSessions.find(
    (item) => item.accountId === accountId && item.workCode === BREAKUP_CODE && item.trial === trial && item.status === "ACTIVE",
  );
  if (existing) {
    return existing;
  }
  const payload = initialPayload(trial);
  const row: MockRuntimeSession = {
    sessionId: crypto.randomUUID().replace(/-/g, ""),
    accountId,
    workCode: BREAKUP_CODE,
    trial,
    payload,
    checksum: checksum(payload),
    status: "ACTIVE",
    schemaVersion: "1",
    form: "A",
    stage: "1",
    version: 0,
  };
  store.runtimeSessions.push(row);
  return row;
}

export function sessionView(row: MockRuntimeSession, includePayload: boolean) {
  return {
    sessionId: row.sessionId,
    workCode: row.workCode,
    form: row.form,
    stage: row.stage,
    schemaVersion: row.schemaVersion,
    checksum: row.checksum,
    status: row.status,
    version: row.version,
    trial: row.trial,
    ...(includePayload ? { payloadJson: JSON.stringify(row.payload) } : {}),
  };
}

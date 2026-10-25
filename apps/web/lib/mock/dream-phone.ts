import { badRequest, forbidden, ok } from "@/lib/mock/http";
import { getStore, type MockRuntimeSession } from "@/lib/mock/store";

export const DREAM_CODE = "dream-phone";
export const PLAYER = "梦女";
export const CHARACTER = "顾西";
export const CHARACTER_ID = "guxi";
export const WALLET = 520;
export const UNCOVERED = "演示模式暂未覆盖这句";
const USER_LINE = "还好，刚下班";
const SCRIPT_REPLY = "那就好。别饿着。";

function checksum(payload: Record<string, unknown>): string {
  const raw = JSON.stringify(payload);
  let hash = 0;
  for (let i = 0; i < raw.length; i += 1) {
    hash = (hash * 31 + raw.charCodeAt(i)) | 0;
  }
  return `mock-${Math.abs(hash).toString(16)}`;
}

function msg(role: string, type: string, content: string, time = Date.now()) {
  return { role, type, content, time };
}

function moment(id: string, charId: string, author: string, emoji: string, content: string, time: number) {
  return {
    id,
    charId,
    author,
    charEmoji: emoji,
    content,
    time,
    liked: false,
    likes: [] as string[],
    comments: [] as Array<{ name: string; text: string }>,
    images: [] as string[],
  };
}

export function initialDreamPayload(trial: boolean): Record<string, unknown> {
  const now = Date.now();
  const moments = [
    moment("m1", CHARACTER_ID, CHARACTER, "🎮", "晚风很好，希望你也早点收工。", now - 3600_000),
  ];
  if (!trial) {
    moments.push(
      moment("m2", CHARACTER_ID, CHARACTER, "🎮", "中午吃了面，下午继续打游戏。", now - 7200_000),
      moment("m3", CHARACTER_ID, CHARACTER, "🎮", "看到一只猫，想起你说过喜欢毛茸茸的。", now - 10800_000),
      moment("m4", "user", PLAYER, "👤", "今天天气不错。", now - 14400_000),
      moment("m5", CHARACTER_ID, CHARACTER, "🎮", "晚上要不要一起听歌？", now - 18000_000),
    );
  }
  return {
    trial,
    work: DREAM_CODE,
    screen: "pageLauncher",
    playerName: PLAYER,
    characterName: CHARACTER,
    currentCharId: CHARACTER_ID,
    wallet: WALLET,
    trialStep: 0,
    fontScale: "md",
    chatBg: "default",
    setupDone: false,
    characters: [{ id: CHARACTER_ID, name: CHARACTER, emoji: "🎮", color: "#6D81A1", avatar: "" }],
    chatHistory: {
      [CHARACTER_ID]: [msg("assistant", "text", "在。刚想到你，今天顺利吗？")],
    },
    messages: [],
    moments,
    momentCount: moments.length,
    memos: trial ? [] : [{ id: "memo1", day: "today", charId: CHARACTER_ID, content: "记得问他晚饭吃什么", time: now }],
    photos: trial ? [] : [{ id: "ph1", url: "", desc: "示例相册", time: now }],
    walletLedger: [],
    charWallets: { [CHARACTER_ID]: WALLET },
    persona: {
      outer: "温柔，会开玩笑，关心日常但不黏人",
      inner: "把在意说成轻的",
      origin: "梦女手机里的默认联系人",
      speech: "短句，口语，不说教",
      redlines: "永远不承认自己是 AI，不打破第四面墙",
    },
    gameRecords: {},
    orders: [],
    musicTracks: [],
    calls: [],
    unread: 0,
  };
}

function viewOf(row: MockRuntimeSession) {
  return {
    sessionId: row.sessionId,
    workCode: DREAM_CODE,
    trial: row.trial,
    playerName: String(row.payload.playerName ?? PLAYER),
    characterName: String(row.payload.characterName ?? CHARACTER),
    uncovered: UNCOVERED,
    payload: row.payload,
  };
}

function persist(row: MockRuntimeSession) {
  row.checksum = checksum(row.payload);
  row.version += 1;
}

function thread(payload: Record<string, unknown>, charId: string) {
  const history = (payload.chatHistory as Record<string, Array<Record<string, unknown>>>) || {};
  if (!history[charId]) {
    history[charId] = [];
  }
  payload.chatHistory = history;
  return history[charId];
}

export async function handleDreamPhoneMock(
  request: Request,
  pathname: string,
  accountId: number,
  requestId: string,
): Promise<Response | null> {
  const store = getStore();
  const method = request.method.toUpperCase();

  const getMatch = pathname.match(/^\/app\/sessions\/([^/]+)\/dream-phone$/);
  if (getMatch && method === "GET") {
    const row = store.runtimeSessions.find((item) => item.sessionId === getMatch[1] && item.accountId === accountId);
    if (!row || row.workCode !== DREAM_CODE) {
      return forbidden(requestId, "session not owned");
    }
    return ok(requestId, viewOf(row));
  }

  const postMatch = pathname.match(/^\/app\/sessions\/([^/]+)\/dream-phone\/actions$/);
  if (postMatch && method === "POST") {
    const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
    const row = store.runtimeSessions.find(
      (item) => item.sessionId === postMatch[1] && item.accountId === accountId && item.workCode === DREAM_CODE,
    );
    if (!row) {
      return forbidden(requestId, "session not owned");
    }
    const type = String(body.type ?? "");
    const charId = String(row.payload.currentCharId ?? CHARACTER_ID);
    if (type === "set-screen") {
      row.payload.screen = String(body.screen ?? row.payload.screen);
      if (body.charId) {
        row.payload.currentCharId = String(body.charId);
      }
    } else if (type === "send") {
      const text = String(body.text ?? "");
      if (row.trial) {
        if (Number(row.payload.trialStep ?? 0) !== 0 || text !== USER_LINE) {
          return badRequest(requestId, UNCOVERED);
        }
        thread(row.payload, charId).push(msg("user", "text", text), msg("assistant", "text", SCRIPT_REPLY));
        row.payload.trialStep = 1;
        row.payload.screen = "pageChat";
      } else {
        thread(row.payload, charId).push(
          msg("user", "text", text),
          msg("assistant", "text", "嗯，我在听。"),
        );
        row.payload.screen = "pageChat";
      }
    } else if (type === "post-moment") {
      if (row.trial) {
        return badRequest(requestId, UNCOVERED);
      }
      const moments = Array.isArray(row.payload.moments) ? row.payload.moments : [];
      moments.unshift(
        moment(`m_${Date.now()}`, "user", PLAYER, "👤", String(body.text ?? ""), Date.now()),
      );
      row.payload.moments = moments;
      row.payload.momentCount = moments.length;
      row.payload.screen = "pageMoments";
    } else if (type === "like-moment") {
      const moments = Array.isArray(row.payload.moments) ? (row.payload.moments as Array<Record<string, unknown>>) : [];
      const target = moments.find((m) => m.id === body.momentId);
      if (target) {
        target.liked = !target.liked;
        target.likes = target.liked ? [PLAYER] : [];
      }
    } else if (type === "comment-moment") {
      const moments = Array.isArray(row.payload.moments) ? (row.payload.moments as Array<Record<string, unknown>>) : [];
      const target = moments.find((m) => m.id === body.momentId);
      if (target) {
        const comments = Array.isArray(target.comments) ? target.comments : [];
        comments.push({ name: PLAYER, text: String(body.text ?? "") });
        target.comments = comments;
      }
    } else if (type === "redpacket" || type === "transfer") {
      if (row.trial) {
        return badRequest(requestId, UNCOVERED);
      }
      const amount = Number(body.amount ?? 0);
      const wallet = Number(row.payload.wallet ?? WALLET);
      if (!(amount > 0)) {
        return badRequest(requestId, "amount must be positive");
      }
      if (amount > wallet) {
        return badRequest(requestId, "钱不够");
      }
      row.payload.wallet = wallet - amount;
      const ledger = Array.isArray(row.payload.walletLedger) ? row.payload.walletLedger : [];
      ledger.unshift({ title: String(body.note || type), amount: -amount, time: Date.now() });
      row.payload.walletLedger = ledger;
      thread(row.payload, charId).push(
        { ...msg("user", type, String(body.note || type)), amount },
        { ...msg("assistant", type, "收到了，谢谢"), amount },
      );
      row.payload.screen = "pageChat";
    } else if (type === "write-memo") {
      if (row.trial) {
        return badRequest(requestId, UNCOVERED);
      }
      const memos = Array.isArray(row.payload.memos) ? row.payload.memos : [];
      memos.unshift({ id: `memo_${Date.now()}`, day: "today", content: String(body.text ?? ""), time: Date.now() });
      row.payload.memos = memos;
      row.payload.screen = "pageMemo";
    } else if (type === "delete-memo") {
      const memos = Array.isArray(row.payload.memos) ? (row.payload.memos as Array<Record<string, unknown>>) : [];
      row.payload.memos = memos.filter((m) => m.id !== body.memoId);
    } else if (type === "add-photo") {
      if (row.trial) {
        return badRequest(requestId, UNCOVERED);
      }
      const photos = Array.isArray(row.payload.photos) ? row.payload.photos : [];
      photos.unshift({ id: `ph_${Date.now()}`, url: "", desc: String(body.desc ?? ""), time: Date.now() });
      row.payload.photos = photos;
      row.payload.screen = "pageGallery";
    } else if (type === "create-char") {
      if (row.trial) {
        return badRequest(requestId, UNCOVERED);
      }
      const id = `c_${Date.now()}`;
      const characters = Array.isArray(row.payload.characters) ? row.payload.characters : [];
      characters.push({ id, name: String(body.name ?? "新角色"), emoji: "😊", color: "#6D81A1", avatar: "" });
      row.payload.characters = characters;
      const wallets = (row.payload.charWallets as Record<string, number>) || {};
      wallets[id] = WALLET;
      row.payload.charWallets = wallets;
      thread(row.payload, id);
      row.payload.currentCharId = id;
      row.payload.screen = "pageWechatMain";
    } else if (type === "delete-char") {
      const id = String(body.charId ?? "");
      if (id === CHARACTER_ID) {
        return badRequest(requestId, "default character cannot be deleted");
      }
      const characters = Array.isArray(row.payload.characters) ? (row.payload.characters as Array<Record<string, unknown>>) : [];
      row.payload.characters = characters.filter((c) => c.id !== id);
      if (row.payload.currentCharId === id) {
        row.payload.currentCharId = CHARACTER_ID;
      }
    } else if (type === "set-font-scale") {
      row.payload.fontScale = String(body.scale ?? "md");
    } else if (type === "set-chat-bg") {
      row.payload.chatBg = String(body.bg ?? "default");
    } else if (type === "complete-setup") {
      row.payload.setupDone = true;
    } else if (type === "regen") {
      const list = thread(row.payload, charId);
      for (let i = list.length - 1; i >= 0; i -= 1) {
        if (list[i].role === "assistant" && (list[i].type ?? "text") === "text") {
          list[i].content = row.trial ? SCRIPT_REPLY : "换一种说法：我在。";
          break;
        }
      }
    } else if (type === "game-result") {
      const game = String(body.game ?? "game");
      const result = String(body.result ?? "draw");
      const records = (row.payload.gameRecords as Record<string, { lastResult?: string; plays?: number }>) || {};
      records[game] = { lastResult: result, plays: (records[game]?.plays ?? 0) + 1 };
      row.payload.gameRecords = records;
      thread(row.payload, charId).push(msg("assistant", "game_result", `${game}：${result}`));
      row.payload.screen = "pageChat";
    } else if (type === "place-order") {
      if (row.trial) {
        return badRequest(requestId, UNCOVERED);
      }
      const app = String(body.app ?? "baoleme");
      const orders = Array.isArray(row.payload.orders) ? row.payload.orders : [];
      orders.unshift({
        id: `ord_${Date.now()}`,
        app,
        title: String(body.title ?? "订单"),
        amount: Number(body.amount ?? 0),
        status: "delivering",
        time: Date.now(),
      });
      row.payload.orders = orders;
      row.payload.screen = app === "jingxi" ? "pageJingxi" : "pageBaoleme";
    } else if (type === "call-end") {
      const calls = Array.isArray(row.payload.calls) ? row.payload.calls : [];
      calls.push({ note: String(body.note || "通话结束"), time: Date.now() });
      row.payload.calls = calls;
      thread(row.payload, charId).push(msg("assistant", "text", String(body.note || "刚才电话挺好的")));
      row.payload.screen = "pageChat";
    } else if (type === "reset") {
      row.payload = initialDreamPayload(row.trial);
    } else {
      return badRequest(requestId, "unknown dream-phone action");
    }
    persist(row);
    return ok(requestId, viewOf(row));
  }

  return null;
}

export function createDreamPhoneSession(accountId: number, trial: boolean): MockRuntimeSession {
  const store = getStore();
  const existing = store.runtimeSessions.find(
    (item) =>
      item.accountId === accountId && item.workCode === DREAM_CODE && item.trial === trial && item.status === "ACTIVE",
  );
  if (existing) {
    return existing;
  }
  const payload = initialDreamPayload(trial);
  const row: MockRuntimeSession = {
    sessionId: crypto.randomUUID().replace(/-/g, ""),
    accountId,
    workCode: DREAM_CODE,
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

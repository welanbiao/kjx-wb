/** Pure DOM painters shaped like v3 renderMsgList / renderChatHistory / renderMoments / renderWallet. */

export type PhoneChar = { id: string; name: string; emoji?: string; color?: string; avatar?: string };
export type PhoneMsg = { role?: string; content?: string; text?: string; type?: string; time?: number; amount?: number; opened?: boolean };
export type PhoneMoment = {
  id?: string;
  author?: string;
  charId?: string;
  charEmoji?: string;
  content?: string;
  time?: number;
  liked?: boolean;
  likes?: string[];
  comments?: Array<{ name?: string; text?: string }>;
};
export type PhoneMemo = { id?: string; content?: string; day?: string; time?: number };
export type PhonePhoto = { id?: string; url?: string; desc?: string; time?: number };
export type PhoneOrder = { id?: string; app?: string; title?: string; amount?: number; status?: string; time?: number };
export type PhoneTrack = { id?: string; title?: string; artist?: string };
export type PhonePayload = {
  screen?: string;
  playerName?: string;
  characterName?: string;
  currentCharId?: string;
  wallet?: number;
  fontScale?: string;
  chatBg?: string;
  setupDone?: boolean;
  trial?: boolean;
  characters?: PhoneChar[];
  chatHistory?: Record<string, PhoneMsg[]>;
  moments?: PhoneMoment[];
  memos?: PhoneMemo[];
  photos?: PhonePhoto[];
  walletLedger?: Array<{ title?: string; amount?: number; time?: number }>;
  charWallets?: Record<string, number>;
  orders?: PhoneOrder[];
  musicTracks?: PhoneTrack[];
  gameRecords?: Record<string, { lastResult?: string; plays?: number }>;
  calls?: Array<{ note?: string; time?: number }>;
};

export function escapeHtml(text: string) {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function clock(ts?: number) {
  const d = new Date(ts || Date.now());
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function dayLabel(ts?: number) {
  const d = new Date(ts || Date.now());
  return `${d.getMonth() + 1}月${d.getDate()}日`;
}

function sameDay(a?: number, b?: number) {
  if (!a || !b) return false;
  const da = new Date(a);
  const db = new Date(b);
  return da.getFullYear() === db.getFullYear() && da.getMonth() === db.getMonth() && da.getDate() === db.getDate();
}

export function applyScreen(root: HTMLElement, screen: string) {
  const tabScreens = new Set(["pageWechatMain", "pageWechatContacts", "pageMoments", "pageWechatMe"]);
  root.querySelectorAll(".app-page").forEach((page) => {
    const on = page.id === screen;
    page.classList.toggle("active", on);
    page.classList.toggle("has-tab", on && tabScreens.has(screen));
  });
  const tabs = root.querySelector("#wechatTabBar");
  tabs?.classList.toggle("is-on", tabScreens.has(screen));
  tabs?.querySelectorAll(".tab-item").forEach((item) => {
    const el = item as HTMLElement;
    el.classList.toggle("active", el.dataset.tab === screen);
  });
  const phone = root.querySelector("#phoneScreen");
  phone?.classList.toggle("desk-mode", screen === "pageLauncher");
  const hour = root.querySelector("#dtHour");
  const status = root.querySelector(".status-bar span");
  const now = new Date();
  const clock = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
  if (hour) hour.textContent = clock;
  if (status) status.textContent = clock;
}

export function fitPhone(root: HTMLElement) {
  const stage = root.querySelector(".phone-stage") as HTMLElement | null;
  const scaler = root.querySelector(".phone-scaler") as HTMLElement | null;
  const wrapper = root.querySelector(".phone-wrapper") as HTMLElement | null;
  if (!stage || !scaler || !wrapper) return;
  const designW = 393;
  const designH = 852;
  const pad = 16;
  const availW = Math.max(280, stage.clientWidth - pad * 2);
  const availH = Math.max(480, stage.clientHeight - pad * 2);
  const scale = Math.min(availW / designW, availH / designH, 1);
  scaler.style.width = `${designW * scale}px`;
  scaler.style.height = `${designH * scale}px`;
  wrapper.style.setProperty("--phone-scale", String(scale));
  wrapper.style.transform = `scale(${scale})`;
}

export function paintMsgList(root: HTMLElement, payload: PhonePayload, onOpen: (id: string) => void) {
  const container = root.querySelector("#msgList");
  if (!container) return;
  const chars = payload.characters ?? [];
  if (!chars.length) {
    container.innerHTML = `<div class="empty-illus"><h4>还没有好友</h4><p>点右上角 + 创建 AI 角色</p></div>`;
    return;
  }
  container.innerHTML = chars
    .map((c) => {
      const hist = (payload.chatHistory?.[c.id] ?? []).filter((m) => m.role !== "system");
      const last = hist[hist.length - 1];
      const preview = last
        ? `${last.role === "user" ? "你: " : ""}${String(last.content || last.text || "").slice(0, 28)}`
        : "开始聊天吧~";
      const avt = c.avatar ? `<img src="${c.avatar}">` : c.emoji || "😊";
      return `<button type="button" class="msg-item" data-open-chat="${c.id}">
        <div class="msg-avatar" style="background:${c.color || "#6D81A1"}">${avt}</div>
        <div class="msg-info"><div class="msg-name">${escapeHtml(c.name)}</div><div class="msg-preview">${escapeHtml(preview)}</div></div>
        <div class="msg-meta"><div class="msg-time">${last?.time ? clock(last.time) : ""}</div></div>
      </button>`;
    })
    .join("");
  container.querySelectorAll<HTMLElement>("[data-open-chat]").forEach((el) => {
    el.addEventListener("click", () => onOpen(el.dataset.openChat || ""));
  });
}

export function paintContacts(root: HTMLElement, payload: PhonePayload, onOpen: (id: string) => void) {
  const container = root.querySelector("#contactsList");
  if (!container) return;
  const chars = payload.characters ?? [];
  container.innerHTML = chars
    .map((c) => {
      const avt = c.avatar ? `<img src="${c.avatar}">` : c.emoji || "😊";
      return `<button type="button" class="msg-item" data-open-chat="${c.id}">
        <div class="msg-avatar" style="background:${c.color || "#6D81A1"}">${avt}</div>
        <div class="msg-info"><div class="msg-name">${escapeHtml(c.name)}</div></div>
      </button>`;
    })
    .join("");
  container.querySelectorAll<HTMLElement>("[data-open-chat]").forEach((el) => {
    el.addEventListener("click", () => onOpen(el.dataset.openChat || ""));
  });
}

function bubbleMeta(ts?: number) {
  return `<span class="bubble-meta">${clock(ts)}</span>`;
}

function avatarHtml(isSelf: boolean, char: PhoneChar | undefined, show: boolean) {
  if (!show) return `<div class="chat-avt spacer"></div>`;
  if (isSelf) return `<div class="chat-avt self">梦</div>`;
  const avt = char?.avatar ? `<img src="${char.avatar}">` : char?.emoji || "😊";
  return `<div class="chat-avt" style="background:${char?.color || "#6D81A1"}">${avt}</div>`;
}

function wrapRow(inner: string, isSelf: boolean, char: PhoneChar | undefined, showAv: boolean) {
  return `<div class="chat-row ${isSelf ? "self" : "other"}${showAv ? "" : " cont"}">${avatarHtml(isSelf, char, showAv)}${inner}</div>`;
}

function specialBubble(m: PhoneMsg, isSelf: boolean, char: PhoneChar | undefined, showAv: boolean) {
  const label =
    m.type === "redpacket"
      ? `[红包] ¥${Number(m.amount || 0).toFixed(2)} · ${m.content || ""}`
      : m.type === "transfer"
        ? `[转账] ¥${Number(m.amount || 0).toFixed(2)} · ${m.content || ""}`
        : m.type === "game_result"
          ? String(m.content || "")
          : `[${m.type}] ${m.content || ""}`;
  if (m.type === "game_result") {
    return `<div class="chat-time-label">${escapeHtml(label)}</div>`;
  }
  return wrapRow(
    `<div class="chat-bubble special"><span class="bubble-text">${escapeHtml(label)}</span>${bubbleMeta(m.time)}</div>`,
    isSelf,
    char,
    showAv,
  );
}

export function paintChat(root: HTMLElement, payload: PhonePayload) {
  const charId = payload.currentCharId || "guxi";
  const char = (payload.characters ?? []).find((c) => c.id === charId);
  const title = root.querySelector("#chatTitle");
  if (title) title.textContent = char?.name || payload.characterName || "聊天";
  const hint = root.querySelector("#chatTypingHint");
  if (hint) {
    (hint as HTMLElement).style.display = "block";
    hint.textContent = "在线";
  }
  const container = root.querySelector("#chatMsgs");
  if (!container) return;
  const msgs = (payload.chatHistory?.[charId] ?? []).filter((m) => m.role !== "system");
  let html = "";
  let lastDay: number | undefined;
  let lastSide: string | null = null;
  msgs.forEach((m) => {
    if (!lastDay || !sameDay(lastDay, m.time)) {
      html += `<div class="chat-time-label">${dayLabel(m.time)}</div>`;
      lastDay = m.time;
      lastSide = null;
    }
    if (m.type === "game_result") {
      html += specialBubble(m, false, char, true);
      lastSide = null;
      return;
    }
    const isSelf = m.role === "user";
    const side = isSelf ? "self" : "other";
    const showAv = side !== lastSide;
    lastSide = side;
    if (m.type && m.type !== "text") {
      html += specialBubble(m, isSelf, char, showAv);
      return;
    }
    const text = m.content || m.text || "";
    html += wrapRow(
      `<div class="chat-bubble"><span class="bubble-text">${escapeHtml(text)}</span>${bubbleMeta(m.time)}</div>`,
      isSelf,
      char,
      showAv,
    );
  });
  container.innerHTML = html;
  container.scrollTop = container.scrollHeight;
}

export function paintMoments(root: HTMLElement, payload: PhonePayload, onLike: (id: string) => void, onComment: (id: string, text: string) => void) {
  const container = root.querySelector("#momentsContent");
  if (!container) return;
  const source = [...(payload.moments ?? [])].sort((a, b) => (b.time || 0) - (a.time || 0));
  if (!source.length) {
    container.innerHTML = `<div class="empty-illus" style="padding-top:28px;"><h4>还没有动态</h4><p>聊天互动后，角色会发朋友圈</p></div>`;
    return;
  }
  container.innerHTML = source
    .map((m) => {
      const likes = (m.likes ?? []).join("、");
      const comments = (m.comments ?? [])
        .map((c) => `<div class="moment-comment"><b>${escapeHtml(c.name || "")}</b>：${escapeHtml(c.text || "")}</div>`)
        .join("");
      return `<article class="moment-card" data-moment-id="${m.id || ""}">
        <div class="moment-hd"><div class="moment-avt">${m.charEmoji || "👤"}</div><div class="moment-name">${escapeHtml(m.author || "")}</div></div>
        <div class="moment-text">${escapeHtml(m.content || "")}</div>
        <div class="moment-ops">
          <button type="button" data-like="${m.id || ""}">${m.liked ? "已赞" : "赞"}</button>
          <button type="button" data-comment="${m.id || ""}">评论</button>
        </div>
        ${likes ? `<div class="moment-likes">${escapeHtml(likes)}</div>` : ""}
        ${comments ? `<div class="moment-comments">${comments}</div>` : ""}
      </article>`;
    })
    .join("");
  container.querySelectorAll<HTMLElement>("[data-like]").forEach((el) => {
    el.addEventListener("click", () => onLike(el.dataset.like || ""));
  });
  container.querySelectorAll<HTMLElement>("[data-comment]").forEach((el) => {
    el.addEventListener("click", () => {
      const text = window.prompt("写评论");
      if (text?.trim()) onComment(el.dataset.comment || "", text.trim());
    });
  });
}

export function paintWallet(root: HTMLElement, payload: PhonePayload) {
  const el = root.querySelector("#walletContent");
  if (!el) return;
  const bal = Number(payload.wallet ?? 520);
  const chars = payload.characters ?? [];
  const charHtml = chars.length
    ? chars
        .map((c) => {
          const amount = payload.charWallets?.[c.id] ?? 520;
          const avt = c.avatar ? `<img src="${c.avatar}">` : c.emoji || "😊";
          return `<div class="wallet-char"><div class="av" style="background:${c.color || "#6D81A1"}">${avt}</div>
            <div class="meta"><div class="nm">${escapeHtml(c.name)}</div><div class="sub">角色钱包</div></div>
            <div class="bal">¥${Number(amount).toFixed(2)}</div></div>`;
        })
        .join("")
    : `<div class="wallet-empty">还没有角色资产</div>`;
  const ledger = payload.walletLedger ?? [];
  const txHtml = ledger.length
    ? ledger
        .slice(0, 40)
        .map((tx) => {
          const plus = (tx.amount ?? 0) >= 0;
          const amt = `${plus ? "+" : ""}¥${Math.abs(tx.amount || 0).toFixed(2)}`;
          return `<div class="wallet-tx"><div class="body"><div class="t">${escapeHtml(tx.title || "")}</div></div>
            <div class="a ${plus ? "plus" : "minus"}">${amt}</div></div>`;
        })
        .join("")
    : `<div class="wallet-empty">暂无流水<br>发红包、转账后会出现在这里</div>`;
  el.innerHTML = `<div class="wallet-hero"><div class="wh-label">我的余额</div><div class="wh-amt">¥${bal.toFixed(2)}</div></div>
    <div class="wallet-sec"><h4>角色资产</h4>${charHtml}</div>
    <div class="wallet-sec"><h4>交易流水</h4>${txHtml}</div>`;
}

export function paintMemos(root: HTMLElement, payload: PhonePayload) {
  const el = root.querySelector("#memoContent");
  if (!el) return;
  const memos = payload.memos ?? [];
  if (!memos.length) {
    el.innerHTML = `<div class="empty-illus"><h4>还没有备忘</h4><p>点右上角新建</p></div>`;
    return;
  }
  el.innerHTML = memos
    .map(
      (m) => `<article class="memo-card" data-memo-id="${m.id || ""}">
      <p>${escapeHtml(m.content || "")}</p>
      <button type="button" data-del-memo="${m.id || ""}">删除</button>
    </article>`,
    )
    .join("");
}

export function paintGallery(root: HTMLElement, payload: PhonePayload) {
  const el = root.querySelector("#galleryGrid");
  if (!el) return;
  const photos = payload.photos ?? [];
  if (!photos.length) {
    el.innerHTML = `<div class="empty-illus"><h4>相册是空的</h4></div>`;
    return;
  }
  el.innerHTML = photos
    .map((p) => `<div class="gallery-item"><div class="gallery-ph">${escapeHtml(p.desc || "照片")}</div></div>`)
    .join("");
}

export function paintMe(root: HTMLElement, payload: PhonePayload) {
  const name = root.querySelector("#userNameDisplay");
  if (name) name.textContent = payload.playerName || "梦女";
  const wallet = root.querySelector("#userWalletDisplay");
  if (wallet) wallet.textContent = `¥${Number(payload.wallet ?? 520).toFixed(2)}`;
  const hint = root.querySelector("#meWalletHint");
  if (hint) hint.textContent = `¥${Number(payload.wallet ?? 520).toFixed(2)}`;
  const count = root.querySelector("#meMomentsCount");
  if (count) count.textContent = String((payload.moments ?? []).filter((m) => m.author === payload.playerName).length);
}

export function paintOrders(root: HTMLElement, payload: PhonePayload, app: "baoleme" | "jingxi") {
  const id = app === "baoleme" ? "#baolemeContent" : "#jingxiContent";
  const el = root.querySelector(id);
  if (!el) return;
  const orders = (payload.orders ?? []).filter((o) => (o.app || "baoleme") === app);
  if (!orders.length) {
    el.innerHTML = `<div class="empty-illus"><h4>还没有订单</h4><p>点下方下单试试</p></div>
      <button type="button" class="kjx-phone-cta" data-place-order="${app}">下单示例</button>`;
    return;
  }
  el.innerHTML =
    orders
      .map(
        (o) => `<div class="shop-card"><div class="shop-info"><div class="shop-name">${escapeHtml(o.title || "")}</div>
      <div class="shop-desc">${escapeHtml(o.status || "")} · ¥${Number(o.amount || 0).toFixed(2)}</div></div></div>`,
      )
      .join("") + `<button type="button" class="kjx-phone-cta" data-place-order="${app}">再下一单</button>`;
}

/** 酷猫画面与曲库由 vendor/kumao-player.ts 接管（本机 IndexedDB，不写会话/库）。 */
export function paintKumao(_root: HTMLElement, _payload: PhonePayload) {
  // no-op
}

export function paintGameSelect(root: HTMLElement, payload: PhonePayload) {
  const records = payload.gameRecords ?? {};
  const tip = root.querySelector("#pageGameSelect .app-content");
  if (!tip) return;
  let banner = tip.querySelector("[data-game-banner]") as HTMLElement | null;
  if (!banner) {
    banner = document.createElement("div");
    banner.dataset.gameBanner = "1";
    banner.style.cssText = "padding:8px 4px 12px;font-size:13px;color:#666;";
    tip.prepend(banner);
  }
  const parts = Object.entries(records).map(([k, v]) => `${k}:${v.lastResult || "-"}`);
  banner.textContent = parts.length ? `战绩 ${parts.join(" · ")}` : "选一个小游戏，结果会回到聊天";
}

export function paintSettings(root: HTMLElement) {
  const wrap = root.querySelector("#settingsContent");
  if (!wrap || wrap.querySelector("[data-member-note]")) return;
  const note = document.createElement("div");
  note.dataset.memberNote = "1";
  note.className = "st-card";
  note.innerHTML = `<div class="st-row col"><div class="st-lab">模型接口</div>
    <div class="st-sub">已改到会员站「设置」。手机内密钥表单不会提交。</div>
    <a class="st-act" href="/settings" data-site-settings="1">打开站点设置</a></div>`;
  wrap.prepend(note);
  wrap.querySelectorAll("[data-url], #apiBaseUrlInput, #modelIdInput, #apiKeyInput, .st-preset, .st-picker, .st-act").forEach((node) => {
    if ((node as HTMLElement).dataset.siteSettings) return;
    if (node instanceof HTMLInputElement || node instanceof HTMLButtonElement) {
      node.disabled = true;
    }
  });
}

export function paintCreateCharList(root: HTMLElement, payload: PhonePayload) {
  let list = root.querySelector("#charListHost") as HTMLElement | null;
  if (!list) {
    list = document.createElement("div");
    list.id = "charListHost";
    list.style.padding = "12px";
    const page = root.querySelector("#pageCreateChar .app-content") || root.querySelector("#pageCreateChar");
    page?.appendChild(list);
  }
  const chars = payload.characters ?? [];
  list.innerHTML =
    chars
      .map(
        (c) => `<div class="shop-card" style="justify-content:space-between;">
      <div class="shop-info"><div class="shop-name">${escapeHtml(c.name)}</div><div class="shop-desc">${c.id}</div></div>
      ${c.id === "guxi" ? "" : `<button type="button" data-del-char="${c.id}">删除</button>`}
    </div>`,
      )
      .join("") +
    `<div class="st-card" style="margin-top:12px;padding:12px;">
      <input id="newCharName" placeholder="新角色名字" style="width:100%;min-height:40px;margin-bottom:8px;" />
      <button type="button" data-create-char="1">创建角色</button>
    </div>`;
}

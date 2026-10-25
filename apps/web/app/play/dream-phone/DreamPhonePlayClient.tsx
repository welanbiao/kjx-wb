"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ErrorBanner } from "@/components/product/Banner";
import { RuntimeSaveGate } from "@/components/runtime/RuntimeSaveGate";
import { isApiError } from "@/lib/errors";
import { runtimeApi, type DreamPhoneView } from "@/runtime/api";
import type { SessionView } from "@/runtime/types";
import {
  applyScreen,
  fitPhone,
  paintChat,
  paintContacts,
  paintCreateCharList,
  paintGallery,
  paintGameSelect,
  paintMe,
  paintMemos,
  paintMoments,
  paintMsgList,
  paintOrders,
  paintSettings,
  paintWallet,
  type PhonePayload,
} from "./vendor/render";
import { mountKumaoPlayer, type KumaoPlayer } from "./vendor/kumao-player";

const WORK = "dream-phone";

const SWITCH: Record<string, string> = {
  wechat: "pageWechatMain",
  contacts: "pageWechatContacts",
  moments: "pageMoments",
  discover: "pageMoments",
  me: "pageWechatMe",
};

const BACK: Record<string, string> = {
  pageChat: "pageWechatMain",
  pageMoments: "pageWechatMain",
  pageWechatMain: "pageLauncher",
  pageWechatContacts: "pageLauncher",
  pageWechatDiscover: "pageLauncher",
  pageWechatMe: "pageLauncher",
  pageWallet: "pageWechatMe",
  pageMemo: "pageLauncher",
  pageGallery: "pageLauncher",
  pageSettings: "pageLauncher",
  pageProfile: "pageWechatMe",
  pageCreateChar: "pageWechatMain",
  pageGameSelect: "pageChat",
  pageGomoku: "pageGameSelect",
  pageBaoleme: "pageLauncher",
  pageJingxi: "pageLauncher",
  pageKumao: "pageLauncher",
  callPage: "pageChat",
};

function asPayload(view: DreamPhoneView): PhonePayload {
  return view.payload as PhonePayload;
}

export function DreamPhonePlayClient({ markup }: { markup: string }) {
  const router = useRouter();
  const search = useSearchParams();
  const wantTrial = search.get("trial") === "1";
  const hostRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<DreamPhoneView | null>(null);
  const mountedRef = useRef(false);
  const busyRef = useRef(false);
  const kumaoRef = useRef<KumaoPlayer | null>(null);
  const [view, setView] = useState<DreamPhoneView | null>(null);
  const [error, setError] = useState<unknown>();
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [gateOpen, setGateOpen] = useState(true);
  const [existing, setExisting] = useState<SessionView | null>(null);
  const [booted, setBooted] = useState(false);

  const act = useCallback(async (body: Record<string, unknown>) => {
    const current = viewRef.current;
    if (!current || busyRef.current) {
      return;
    }
    busyRef.current = true;
    setBusy(true);
    setNotice(null);
    try {
      const next = (await runtimeApi.dreamPhoneAction(current.sessionId, body)).data;
      viewRef.current = next;
      setView(next);
    } catch (err) {
      if (isApiError(err)) {
        setNotice(err.message);
        return;
      }
      setError(err);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }, []);

  const paint = useCallback(
    (next: DreamPhoneView) => {
      const host = hostRef.current;
      if (!host) {
        return;
      }
      const payload = asPayload(next);
      const screen = payload.screen || "pageLauncher";
      applyScreen(host, screen);
      const call = host.querySelector("#callPage") as HTMLElement | null;
      if (call) {
        call.classList.toggle("show", screen === "callPage");
        call.style.display = screen === "callPage" ? "flex" : "none";
      }
      paintMsgList(host, payload, (id) => void act({ type: "set-screen", screen: "pageChat", charId: id }));
      paintContacts(host, payload, (id) => void act({ type: "set-screen", screen: "pageChat", charId: id }));
      paintChat(host, payload);
      paintMoments(
        host,
        payload,
        (id) => void act({ type: "like-moment", momentId: id }),
        (id, text) => void act({ type: "comment-moment", momentId: id, text }),
      );
      paintWallet(host, payload);
      paintMemos(host, payload);
      paintGallery(host, payload);
      paintMe(host, payload);
      paintOrders(host, payload, "baoleme");
      paintOrders(host, payload, "jingxi");
      paintGameSelect(host, payload);
      kumaoRef.current?.setScreen(screen);
      void kumaoRef.current?.refresh();
      paintSettings(host);
      paintCreateCharList(host, payload);
      const tour = host.querySelector("#tourOverlay");
      if (tour) {
        const show = !payload.setupDone && !next.trial;
        tour.classList.toggle("show", show);
        tour.setAttribute("aria-hidden", show ? "false" : "true");
      }
      const phone = host.querySelector("#phoneScreen") as HTMLElement | null;
      if (phone && payload.fontScale) {
        phone.dataset.font = payload.fontScale;
      }
      const chatPage = host.querySelector("#pageChat") as HTMLElement | null;
      if (chatPage && payload.chatBg) {
        chatPage.dataset.bg = payload.chatBg;
      }
      fitPhone(host);
      host.querySelectorAll<HTMLButtonElement>("[data-del-memo]").forEach((el) => {
        el.onclick = () => void act({ type: "delete-memo", memoId: el.dataset.delMemo || "" });
      });
      host.querySelectorAll<HTMLButtonElement>("[data-del-char]").forEach((el) => {
        el.onclick = () => void act({ type: "delete-char", charId: el.dataset.delChar || "" });
      });
      host.querySelectorAll<HTMLButtonElement>("[data-create-char]").forEach((el) => {
        el.onclick = () => {
          const input = host.querySelector("#newCharName") as HTMLInputElement | null;
          const name = input?.value.trim() ?? "";
          if (name) {
            void act({ type: "create-char", name });
          }
        };
      });
      host.querySelectorAll<HTMLButtonElement>("[data-place-order]").forEach((el) => {
        el.onclick = () => {
          const app = el.dataset.placeOrder || "baoleme";
          void act({
            type: "place-order",
            app,
            title: app === "jingxi" ? "示例礼物" : "示例外卖",
            amount: app === "jingxi" ? "52" : "26",
          });
        };
      });
    },
    [act],
  );

  const bindShell = useCallback(
    (host: HTMLElement) => {
      const go = (next: string, extra?: Record<string, unknown>) =>
        void act({ type: "set-screen", screen: next, ...extra });
      host.querySelectorAll<HTMLElement>("[data-go]").forEach((node) => {
        node.addEventListener("click", () => go(node.dataset.go || "pageLauncher"));
      });
      host.querySelectorAll<HTMLElement>("[data-switch]").forEach((node) => {
        node.addEventListener("click", () => go(SWITCH[node.dataset.switch || ""] || "pageWechatMain"));
      });
      host.querySelectorAll<HTMLElement>("[data-home]").forEach((node) => {
        node.addEventListener("click", () => go("pageLauncher"));
      });
      host.querySelectorAll<HTMLElement>("[data-back]").forEach((node) => {
        node.addEventListener("click", () => {
          const screen = viewRef.current?.payload.screen || "pageLauncher";
          go(BACK[screen] || "pageLauncher");
        });
      });
      host.querySelector("#baolemeBackBtn")?.addEventListener("click", () => go("pageLauncher"));
      host.querySelector(".plus-btn")?.addEventListener("click", () => {
        host.querySelector("#plusOverlay")?.classList.toggle("show");
      });
      host.querySelectorAll<HTMLElement>(".plus-menu-item").forEach((node) => {
        node.addEventListener("click", () => {
          const label = node.textContent?.trim() ?? "";
          host.querySelector("#plusOverlay")?.classList.remove("show");
          if (label.includes("红包")) {
            void act({ type: "redpacket", amount: "5.2", note: "给你一个小红包~" });
          } else if (label.includes("转账")) {
            void act({ type: "transfer", amount: "10", note: "转账" });
          } else if (label.includes("小游戏")) {
            go("pageGameSelect");
          } else if (label.includes("相册")) {
            go("pageGallery");
          } else if (label.includes("音乐")) {
            go("pageKumao");
          }
        });
      });
      host.querySelector("[data-send]")?.addEventListener("click", () => {
        const input = host.querySelector("#chatInput") as HTMLTextAreaElement | null;
        const text = input?.value.trim() ?? "";
        if (!text) {
          return;
        }
        if (input) {
          input.value = "";
        }
        void act({ type: "send", text });
      });
      const chatInput = host.querySelector("#chatInput") as HTMLTextAreaElement | null;
      chatInput?.addEventListener("input", () => {
        const send = host.querySelector("#sendBtn") as HTMLButtonElement | null;
        if (send) {
          send.disabled = !(chatInput.value.trim().length > 0);
        }
      });
      host.querySelector("[data-moment-compose]")?.addEventListener("click", () => {
        const area = host.querySelector("#postMomentArea") as HTMLElement | null;
        if (area) {
          area.style.display = area.style.display === "none" ? "block" : "none";
        }
      });
      host.querySelector("[data-moment-submit]")?.addEventListener("click", () => {
        const input = host.querySelector("#postMomentText") as HTMLTextAreaElement | null;
        const text = input?.value.trim() ?? "";
        if (!text) {
          return;
        }
        void act({ type: "post-moment", text });
      });
      host.querySelector(".memo-add")?.addEventListener("click", () => {
        const text = window.prompt("写一条备忘");
        if (text?.trim()) {
          void act({ type: "write-memo", text: text.trim() });
        }
      });
      host.querySelector("#pageGallery .nav-btn:last-child")?.addEventListener("click", () => {
        const desc = window.prompt("照片描述", "一张新照片");
        if (desc != null) {
          void act({ type: "add-photo", desc: desc.trim() || "一张新照片" });
        }
      });
      const gamePage = host.querySelector("#pageGameSelect .app-content");
      gamePage?.querySelectorAll(".shop-card").forEach((card, index) => {
        const games = ["gomoku", "turtle", "xiangqi", "mines"];
        const game = games[index] || `game${index}`;
        card.setAttribute("role", "button");
        (card as HTMLElement).style.cursor = "pointer";
        card.addEventListener("click", () => {
          if (game === "gomoku") {
            go("pageGomoku");
            return;
          }
          void act({ type: "game-result", game, result: "draw" });
        });
      });
      host.querySelector("#pageGomoku .nav-btn.primary")?.addEventListener("click", () => {
        const board = host.querySelector("#gomokuContainer");
        if (board) {
          board.innerHTML = `<div style="padding:24px;text-align:center;">
            <p>本地五子棋占位棋盘</p>
            <button type="button" data-gomoku-win="1">我赢了</button>
            <button type="button" data-gomoku-lose="1">我输了</button>
          </div>`;
          board.querySelector("[data-gomoku-win]")?.addEventListener("click", () => {
            void act({ type: "game-result", game: "gomoku", result: "win" });
          });
          board.querySelector("[data-gomoku-lose]")?.addEventListener("click", () => {
            void act({ type: "game-result", game: "gomoku", result: "lose" });
          });
        }
      });
      host.querySelector("#pageGomoku")?.addEventListener("click", () => {
        // ensure board exists when entering
      });
      const ensureGomoku = () => {
        const board = host.querySelector("#gomokuContainer");
        if (board && !board.querySelector("[data-gomoku-win]")) {
          board.innerHTML = `<div style="padding:24px;text-align:center;">
            <p>本地五子棋 · 结果回聊</p>
            <button type="button" data-gomoku-win="1">我赢了</button>
            <button type="button" data-gomoku-lose="1">我输了</button>
          </div>`;
          board.querySelector("[data-gomoku-win]")?.addEventListener("click", () => {
            void act({ type: "game-result", game: "gomoku", result: "win" });
          });
          board.querySelector("[data-gomoku-lose]")?.addEventListener("click", () => {
            void act({ type: "game-result", game: "gomoku", result: "lose" });
          });
        }
      };
      const observer = new MutationObserver(() => {
        if (viewRef.current?.payload.screen === "pageGomoku") {
          ensureGomoku();
        }
      });
      observer.observe(host, { attributes: true, subtree: true, attributeFilter: ["class", "style"] });
      host.querySelector("#tourOverlay .tour-skip")?.addEventListener("click", () => {
        void act({ type: "complete-setup" });
      });
      host.querySelector("#tourNext")?.addEventListener("click", () => {
        void act({ type: "complete-setup" });
      });
      // regen bar under last assistant bubble
      const regenHost = document.createElement("div");
      regenHost.id = "regenBarHost";
      regenHost.style.cssText = "padding:0 12px 8px;";
      regenHost.innerHTML = `<button type="button" class="regen-btn" data-regen="1" aria-label="再生回复">↻</button>
        <button type="button" data-call="1" style="margin-left:8px;">电话</button>
        <button type="button" data-chat-bg="1" style="margin-left:8px;">背景</button>`;
      host.querySelector("#pageChat .chat-input-area")?.insertAdjacentElement("beforebegin", regenHost);
      regenHost.querySelector("[data-regen]")?.addEventListener("click", () => void act({ type: "regen" }));
      regenHost.querySelector("[data-call]")?.addEventListener("click", () => go("callPage"));
      regenHost.querySelector("[data-chat-bg]")?.addEventListener("click", () => {
        const bg = window.prompt("聊天背景键", viewRef.current?.payload.chatBg || "default");
        if (bg != null) {
          void act({ type: "set-chat-bg", bg: bg.trim() || "default" });
        }
      });
      host.querySelector("#callPage .call-btn.hangup")?.addEventListener("click", () => {
        void act({ type: "call-end", note: "通话结束" });
      });
      // create-char entry from wechat main +
      host.querySelector("#pageWechatMain .nav-btn.primary, #pageWechatMain [data-create]")?.addEventListener("click", () => {
        go("pageCreateChar");
      });
      const createBtn = host.querySelector("#pageWechatMain .nav-bar .nav-btn:last-child");
      createBtn?.addEventListener("click", () => go("pageCreateChar"));
      window.addEventListener("resize", () => fitPhone(host));
    },
    [act],
  );

  const mountShell = useCallback(() => {
    const host = hostRef.current;
    if (!host || mountedRef.current) {
      return;
    }
    const styleMatch = markup.match(/<style>([\s\S]*?)<\/style>/);
    const bodyMatch = markup.match(/<body>([\s\S]*?)<\/body>/);
    host.innerHTML = `<style>${styleMatch?.[1] ?? ""}
      html,body{height:auto!important;min-height:0!important;overflow:auto!important;background:transparent!important;}
      .dream-host .phone-stage{height:min(92vh,900px);min-height:640px;background:transparent;}
      .kjx-phone-cta{margin-top:12px;min-height:40px;padding:0 14px;border-radius:10px;border:1px solid #c9d4e3;background:#fff;cursor:pointer;}
      .chat-row{display:flex;gap:8px;margin:6px 12px;align-items:flex-end;}
      .chat-row.self{flex-direction:row-reverse;}
      .chat-row.cont .chat-avt.spacer{visibility:hidden;}
      .chat-avt{width:36px;height:36px;border-radius:8px;overflow:hidden;flex-shrink:0;display:flex;align-items:center;justify-content:center;background:#6D81A1;color:#fff;font-size:14px;}
      .chat-avt img{width:100%;height:100%;object-fit:cover;}
      .chat-bubble{max-width:72%;padding:8px 12px;border-radius:12px;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,.06);position:relative;}
      .chat-row.self .chat-bubble{background:#95EC69;}
      .chat-bubble.special{background:#FFF7E8;}
      .bubble-meta{display:block;margin-top:4px;font-size:10px;opacity:.55;}
      .chat-time-label{text-align:center;font-size:12px;color:#8E8E93;margin:10px 0;}
      .moment-card{padding:14px 16px;border-bottom:1px solid #eef1f5;}
      .moment-ops{display:flex;gap:12px;margin-top:8px;}
      .moment-ops button{border:none;background:transparent;color:#4A90E2;cursor:pointer;}
      .wallet-hero{padding:24px 16px;background:linear-gradient(160deg,#4A90E2,#6BB3FF);color:#fff;border-radius:16px;margin:12px;}
      .wh-amt{font-size:32px;font-weight:700;margin-top:6px;}
      .wallet-sec{padding:12px 16px;}
      .wallet-char,.wallet-tx{display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid #eef1f5;}
      .wallet-char .bal{margin-left:auto;font-weight:600;}
      .wallet-tx .a{margin-left:auto;}
      .wallet-tx .a.plus{color:#2f9e44;}
      .wallet-tx .a.minus{color:#e03131;}
      .memo-card{padding:12px;border-radius:12px;background:#fff8db;min-height:80px;}
      .gallery-item{aspect-ratio:1;background:#eef2f7;border-radius:8px;display:flex;align-items:center;justify-content:center;}
      .gallery-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;}
      /* 紧凑灵动岛：圆环进度 + 中间滚动曲名，不撑破 126×36 */
      .live-activity.show:not(.expanded){overflow:hidden;}
      .live-activity:not(.expanded) .la-row-compact{gap:5px;min-width:0;}
      .live-activity:not(.expanded) .la-icon-wrap{
        position:relative;width:22px;height:22px;flex-shrink:0;
      }
      .live-activity:not(.expanded) .la-icon-wrap .la-ring{
        position:absolute;inset:-2.5px;border-radius:50%;pointer-events:none;
        background:conic-gradient(#BF5AF2 calc(var(--la-p,0) * 1%), rgba(255,255,255,.16) 0);
        -webkit-mask:radial-gradient(farthest-side,#0000 calc(100% - 2px),#000 0);
        mask:radial-gradient(farthest-side,#0000 calc(100% - 2px),#000 0);
      }
      .live-activity:not(.expanded) .la-icon-wrap .la-icon{
        position:relative;z-index:1;width:22px;height:22px;border-radius:50%;font-size:10px;
        background:#2a2a2e;
      }
      .live-activity:not(.expanded) .la-ticker{
        flex:1 1 auto;min-width:0;max-width:100%;
        height:18px;overflow:hidden;display:flex;align-items:center;
        -webkit-mask-image:linear-gradient(90deg,transparent,#000 10%,#000 90%,transparent);
        mask-image:linear-gradient(90deg,transparent,#000 10%,#000 90%,transparent);
      }
      .live-activity:not(.expanded) .la-ticker-inner{
        display:inline-flex;align-items:center;gap:28px;white-space:nowrap;
        font-size:10px;font-weight:600;color:#fff;
        line-height:18px;letter-spacing:.01em;
        will-change:transform;animation:laMarquee 9s linear infinite;
      }
      .live-activity:not(.playing) .la-ticker-inner{animation-play-state:paused;}
      @keyframes laMarquee{from{transform:translateX(0)}to{transform:translateX(-50%)}}
    </style>${bodyMatch?.[1] ?? ""}`;
    bindShell(host);
    kumaoRef.current?.destroy();
    kumaoRef.current = mountKumaoPlayer(host);
    mountedRef.current = true;
    fitPhone(host);
  }, [bindShell, markup]);

  async function bootSession(mode: "continue" | "new" | "reset") {
    setError(undefined);
    setBusy(true);
    try {
      let listed: { data: SessionView[] };
      try {
        listed = await runtimeApi.listSessions(WORK);
      } catch (err) {
        if (isApiError(err) && err.httpStatus === 403) {
          listed = { data: [] };
        } else {
          throw err;
        }
      }
      const match = listed.data.find((item) => item.status === "ACTIVE" && Boolean(item.trial) === wantTrial) ?? null;
      setExisting(match);
      if (mode === "continue" && match) {
        const next = (await runtimeApi.dreamPhoneView(match.sessionId)).data;
        viewRef.current = next;
        setView(next);
        setGateOpen(false);
        setBooted(true);
        return;
      }
      if (mode === "reset" && match) {
        const next = (await runtimeApi.dreamPhoneAction(match.sessionId, { type: "reset" })).data;
        viewRef.current = next;
        setView(next);
        setGateOpen(false);
        setBooted(true);
        return;
      }
      // new: if existing active, reset it; else create
      if (match) {
        const next = (await runtimeApi.dreamPhoneAction(match.sessionId, { type: "reset" })).data;
        viewRef.current = next;
        setView(next);
      } else {
        const session = (await runtimeApi.createSessionWithTrial(WORK, wantTrial)).data;
        const next = (await runtimeApi.dreamPhoneView(session.sessionId)).data;
        viewRef.current = next;
        setView(next);
      }
      setGateOpen(false);
      setBooted(true);
    } catch (err) {
      if (isApiError(err) && err.httpStatus === 401) {
        router.replace(`/login?next=/play/${WORK}${wantTrial ? "?trial=1" : ""}`);
        return;
      }
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    async function probe() {
      try {
        let listed: { data: SessionView[] };
        try {
          listed = await runtimeApi.listSessions(WORK);
        } catch (err) {
          if (isApiError(err) && err.httpStatus === 403) {
            listed = { data: [] };
          } else if (isApiError(err) && err.httpStatus === 401) {
            router.replace(`/login?next=/play/${WORK}${wantTrial ? "?trial=1" : ""}`);
            return;
          } else {
            throw err;
          }
        }
        if (cancelled) {
          return;
        }
        const match = listed.data.find((item) => item.status === "ACTIVE" && Boolean(item.trial) === wantTrial) ?? null;
        setExisting(match);
        setGateOpen(true);
      } catch (err) {
        if (!cancelled) {
          setError(err);
        }
      }
    }
    void probe();
    return () => {
      cancelled = true;
    };
  }, [router, wantTrial]);

  useEffect(() => {
    if (!booted || !view) {
      return;
    }
    mountShell();
    paint(view);
  }, [booted, view, mountShell, paint]);

  // 退出 /play/dream-phone 或 /runtime/dream-phone 时停掉本机播放与灵动岛
  useEffect(() => {
    return () => {
      kumaoRef.current?.destroy();
      kumaoRef.current = null;
      mountedRef.current = false;
    };
  }, []);

  return (
    <section className="kjx-section">
      <div className="kjx-wrap">
        {error ? <ErrorBanner error={error} /> : null}
        {notice ? <p className="kjx-hint">{notice}</p> : null}
        {gateOpen ? (
          <RuntimeSaveGate
            hasSave={Boolean(existing)}
            busy={busy}
            copy={{
              title: "梦女小手机",
              detected: "检测到服务器存档，可继续或重置。",
              empty: "尚无存档。开始后写入服务器会话。",
              continueLabel: "继续游玩",
              newLabel: "新开一局",
              resetLabel: "重置存档",
            }}
            onContinue={() => void bootSession("continue")}
            onNew={() => void bootSession("new")}
            onReset={() => void bootSession("reset")}
          />
        ) : null}
        <div className="dream-host" ref={hostRef} hidden={gateOpen} />
      </div>
    </section>
  );
}

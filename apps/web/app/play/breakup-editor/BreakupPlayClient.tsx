"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { ErrorBanner } from "@/components/product/Banner";
import { isApiError } from "@/lib/errors";
import { runtimeApi, type BreakupView } from "@/runtime/api";
import type { SessionView } from "@/runtime/types";
import { ORIGINAL_MARKUP } from "./originalMarkup";

const WORK = "breakup-editor";

type Payload = Record<string, unknown>;

function asRecord(value: unknown): Payload {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as Payload) : {};
}

function asList(value: unknown): Payload[] {
  return Array.isArray(value) ? value.filter((item) => item && typeof item === "object") as Payload[] : [];
}

function assetUrl(assets: Record<string, string>, key: string): string {
  const normalized = key.replace(/^assets\//, "");
  return assets[normalized] || assets[key] || "";
}

function rewriteCssUrls(css: string, assets: Record<string, string>) {
  return css.replace(/url\(\s*(['"]?)(?:\.\.\/)*assets\/([^'")]+)\1\s*\)/g, (_match, _quote, path: string) => {
    const url = assetUrl(assets, path.split("?")[0]);
    return url ? `url("${url}")` : `url("assets/${path}")`;
  });
}

function lockPhoneHtml(side: "a" | "b", name: string, wallpaper: string) {
  const label = `${name}的手机`;
  const wp = wallpaper
    ? `<img class="wall2-phone-wp" src="${wallpaper}" alt="" draggable="false" />`
    : `<span class="wall2-phone-wp-fallback"></span>`;
  return `<button type="button" class="wall2-phone wall2-phone-live" data-side="${side}" aria-label="${label}"><span class="wall2-phone-face"><span class="wall2-phone-screen">${wp}<span class="wall2-phone-di"></span><span class="wall2-phone-status"><i>9:41</i><i class="wall2-phone-sig"></i></span><span class="wall-phone-lock-clock"><span class="wall-lock-top"><span class="wall-lock-date">9月9日 星期二</span><span class="wall-lock-time"><span class="wall-lock-time-text">9:41</span></span></span></span></span></span></button>`;
}
function rewriteAssets(root: HTMLElement, assets: Record<string, string>) {
  root.querySelectorAll("img[src]").forEach((node) => {
    const img = node as HTMLImageElement;
    const src = img.getAttribute("src") ?? "";
    if (src.includes("assets/")) {
      const next = assetUrl(assets, src);
      if (next) {
        img.src = next;
      }
    }
  });
}

const PROLOGUE_BEATS = [
  ["七月七，七夕。", "和往年一样，今年的这一天，你也是一个人过。"],
  ["下班走出公司大门，你像往常一样低头刷手机。"],
  ["朋友圈里全是牵手照，和那句", "「七夕快乐」。", "你一条条划过。"],
  ["最后停在聊天列表上——", "空荡荡的，没有一个红点。"],
  ["忽然，你看见地上躺着一部手机。", "屏幕还亮着。"],
  ["某个陌生人的聊天界面，在雨夜里发着光。"],
  ["你捡起来。", "鬼使神差地，滑开了它。"],
];

function paintPrologue(root: HTMLElement, index: number) {
  const text = root.querySelector("#prologue-text");
  const pick = root.querySelector("#prologue-pick") as HTMLButtonElement | null;
  const hint = root.querySelector("#prologue-hint") as HTMLElement | null;
  if (!text) {
    return;
  }
  const shown = PROLOGUE_BEATS.slice(0, index + 1);
  text.innerHTML = shown
    .map((beat) => `<p>${beat.map((line) => line.replace(/[&<>]/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" })[ch] || ch)).join("")}</p>`)
    .join("");
  const last = index >= PROLOGUE_BEATS.length - 1;
  if (pick) {
    pick.hidden = !last;
  }
  if (hint) {
    hint.hidden = last;
  }
}
const WALL_FRONT = { tx: -52, ty: -6, rot: -17, scale: 1, opacity: 1, z: 10 };
const WALL_BACK = { tx: 46, ty: 14, rot: 14, scale: 0.92, opacity: 0.78, z: 5 };

function wallPos(pos: typeof WALL_FRONT) {
  return `transform:translate(${pos.tx}px,${pos.ty}px) rotate(${pos.rot}deg) scale(${pos.scale});opacity:${pos.opacity};z-index:${pos.z};`;
}

function renderWall(root: HTMLElement, view: BreakupView, onPick: (side: "a" | "b") => void, onLocked: () => void) {
  const stage = root.querySelector("#wall2-stage");
  const tabs = root.querySelector("#wall2-tabs");
  const cards = root.querySelector("#wall2-cards");
  const dots = root.querySelector("#wall2-dots");
  if (!stage || !tabs || !cards) {
    return;
  }
  const slides = view.wall.slides;
  const index = Math.max(0, Math.min(slides.length - 1, Number(stage.getAttribute("data-index") || "0")));
  stage.setAttribute("data-index", String(index));
  const slide = slides[index];
  if (!slide) {
    return;
  }
  const names = slide.couple.split("×").map((part) => part.trim());
  const leftName = names[0] || "他";
  const rightName = names[1] || "她";
  const couple = asRecord(view.couple);
  const hers = asRecord(couple.a);
  const his = asRecord(couple.b);
  const playable = Boolean(slide.playable) && !slide.locked;
  const leftSide: "a" | "b" = playable ? "b" : "a";
  const rightSide: "a" | "b" = leftSide === "a" ? "b" : "a";
  const people = {
    a: { name: String(hers.name || rightName), gender: "女", status: String(hers.wallStatus || hers.sidePick || "") },
    b: { name: String(his.name || leftName), gender: "男", status: String(his.wallStatus || his.sidePick || "") },
  };
  if (!playable) {
    people.a = { name: leftName, gender: "男", status: slide.gold || slide.category };
    people.b = { name: rightName, gender: "女", status: slide.category };
  }
  const active: "a" | "b" = (stage.getAttribute("data-active") as "a" | "b") || leftSide;
  stage.setAttribute("data-active", active);
  const wall = root.querySelector("#screen-wall") as HTMLElement | null;
  wall?.style.setProperty("--wall2-blue", slide.wallAccent || "#2563eb");
  const typeEl = root.querySelector("#wall-slide-type");
  if (typeEl) {
    typeEl.textContent = slide.typeTag ? `“${slide.typeTag.replace(/^["“]|["”]$/g, "")}”` : "";
  }
  const tip = root.querySelector("#wall-tip-text");
  if (tip) {
    tip.textContent = slide.gold || view.wall.gold || "";
  }
  const pill = root.querySelector("#wall-demo-pill") as HTMLElement | null;
  if (pill) {
    pill.hidden = slide.locked;
  }
  stage.querySelectorAll(".wall2-phone").forEach((node) => node.remove());
  const wallpaperFor = (side: "a" | "b") => {
    if (!playable) {
      return "";
    }
    return assetUrl(view.assets, side === "b" ? "wallpaper-asphyxia-him.webp" : "wallpaper-asphyxia-her.webp");
  };
  ([leftSide, rightSide] as const).forEach((side) => {
    const wallpaper = wallpaperFor(side);
    const shot = assetUrl(view.assets, side === leftSide ? slide.leftPhone : slide.rightPhone);
    const holder = document.createElement("div");
    if (wallpaper) {
      holder.innerHTML = lockPhoneHtml(side, people[side].name, wallpaper);
    } else if (shot) {
      holder.innerHTML = `<button type="button" class="wall2-phone" data-side="${side}" aria-label="${people[side].name}的手机"><img class="wall2-phone-shot" alt="" draggable="false" src="${shot}" /></button>`;
    } else {
      return;
    }
    const phone = holder.firstElementChild as HTMLButtonElement | null;
    if (!phone) {
      return;
    }
    phone.classList.toggle("is-front", side === active);
    phone.setAttribute("style", wallPos(side === active ? WALL_FRONT : WALL_BACK));
    phone.addEventListener("click", () => {
      if (slide.locked || !slide.playable) {
        onLocked();
        return;
      }
      if (side !== active) {
        stage.setAttribute("data-active", side);
        renderWall(root, view, onPick, onLocked);
        return;
      }
      onPick(side);
    });
    stage.appendChild(phone);
  });
  tabs.innerHTML = "";
  ([leftSide, rightSide] as const).forEach((side) => {
    const tab = document.createElement("button");
    tab.type = "button";
    tab.className = `wall2-tab${side === active ? " is-active" : ""}`;
    tab.dataset.side = side;
    tab.textContent = people[side].name;
    tab.addEventListener("click", () => {
      stage.setAttribute("data-active", side);
      renderWall(root, view, onPick, onLocked);
    });
    tabs.appendChild(tab);
  });
  const inactive: "a" | "b" = active === "a" ? "b" : "a";
  cards.innerHTML = "";
  [inactive, active].forEach((side) => {
    const person = people[side];
    const card = document.createElement("button");
    card.type = "button";
    card.className = `wall2-card${side === active ? " is-active" : ""}`;
    card.dataset.side = side;
    card.innerHTML = `<span class="wall2-card-gender">${person.gender}</span><span class="wall2-card-rule"></span><span class="wall2-card-body"><p class="wall2-card-title">点击解锁${person.name}的手机</p><p class="wall2-card-status">${person.status}</p></span>`;
    card.addEventListener("click", () => {
      if (side !== active) {
        stage.setAttribute("data-active", side);
        renderWall(root, view, onPick, onLocked);
        return;
      }
      if (slide.locked || !slide.playable) {
        onLocked();
        return;
      }
      onPick(side);
    });
    cards.appendChild(card);
  });
  if (dots) {
    dots.innerHTML = "";
    slides.forEach((item, dotIndex) => {
      const dot = document.createElement("button");
      dot.type = "button";
      dot.className = `wall2-dot${dotIndex === index ? " is-active" : ""}`;
      dot.addEventListener("click", () => {
        stage.setAttribute("data-index", String(dotIndex));
        stage.setAttribute("data-active", "");
        renderWall(root, view, onPick, onLocked);
      });
      dots.appendChild(dot);
    });
  }
  const go = (delta: number) => {
    const next = Math.max(0, Math.min(slides.length - 1, index + delta));
    stage.setAttribute("data-index", String(next));
    stage.setAttribute("data-active", "");
    renderWall(root, view, onPick, onLocked);
  };
  root.querySelector("#wall2-prev")?.addEventListener("click", () => go(-1));
  root.querySelector("#wall2-next")?.addEventListener("click", () => go(1));
}

function renderWallpaper(root: HTMLElement, view: BreakupView, payload: Payload) {
  const wallpaper = root.querySelector("#home-wallpaper");
  if (!wallpaper) {
    return;
  }
  wallpaper.innerHTML = "";
  const preferred =
    payload.side === "b" ? "wallpaper-asphyxia-her.webp" : "wallpaper-asphyxia-him-home.webp";
  const src =
    assetUrl(view.assets, preferred) ||
    assetUrl(view.assets, "wallpaper-dusk.webp") ||
    assetUrl(view.assets, "wallpaper-asphyxia-him.webp");
  if (!src) {
    return;
  }
  const img = document.createElement("img");
  img.alt = "";
  img.draggable = false;
  img.src = src;
  wallpaper.appendChild(img);
}

function showPhoneToast(host: HTMLElement, message: string) {
  const stack = host.querySelector("#toast-stack");
  if (!stack) {
    return;
  }
  stack.innerHTML = "";
  if (!message) {
    return;
  }
  const toast = document.createElement("div");
  toast.className = "toast";
  toast.setAttribute("role", "alert");
  toast.textContent = message;
  stack.appendChild(toast);
}

function openRewriteModal(host: HTMLElement, initial: string, onSave: (text: string) => void) {
  const mask = host.querySelector("#modal-mask") as HTMLElement | null;
  const title = host.querySelector("#modal-title");
  const body = host.querySelector("#modal-body");
  const actions = host.querySelector("#modal-actions");
  const close = host.querySelector("#modal-close");
  if (!mask || !body || !actions) {
    return;
  }
  if (title) {
    title.textContent = "改写这句";
  }
  body.innerHTML = "";
  const input = document.createElement("textarea");
  input.id = "rewrite-input";
  input.value = initial;
  input.rows = 4;
  input.style.width = "100%";
  body.appendChild(input);
  actions.innerHTML = "";
  const save = document.createElement("button");
  save.type = "button";
  save.className = "btn primary";
  save.textContent = "写入";
  const cancel = document.createElement("button");
  cancel.type = "button";
  cancel.className = "btn";
  cancel.textContent = "取消";
  const hide = () => mask.classList.remove("show");
  save.addEventListener("click", () => {
    const next = input.value.trim();
    hide();
    if (next && next !== initial) {
      onSave(next);
    }
  });
  cancel.addEventListener("click", hide);
  close?.addEventListener("click", hide, { once: true });
  actions.append(save, cancel);
  mask.classList.add("show");
  input.focus();
}

export function BreakupPlayClient() {
  const router = useRouter();
  const search = useSearchParams();
  const wantTrial = search.get("trial") === "1";
  const hostRef = useRef<HTMLDivElement>(null);
  const keyRef = useRef({ apiKey: "", apiBase: "", apiModel: "" });
  const [view, setView] = useState<BreakupView | null>(null);
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const payload = useMemo(() => asRecord(view?.payload), [view]);

  async function load() {
    setError(undefined);
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
      const trialRequested = wantTrial;
      const existing = listed.data.find(
        (item) => item.status === "ACTIVE" && Boolean(item.trial) === trialRequested,
      );
      const session = existing ?? (await runtimeApi.createSessionWithTrial(WORK, trialRequested)).data;
      const next = (await runtimeApi.breakupView(session.sessionId)).data;
      setView(next);
    } catch (err) {
      if (isApiError(err) && err.httpStatus === 401) {
        router.replace(`/login?next=/play/${WORK}${wantTrial ? "?trial=1" : ""}`);
        return;
      }
      setError(err);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wantTrial]);

  async function act(body: Record<string, unknown>) {
    if (!view || busy) {
      return;
    }
    setBusy(true);
    setNotice(null);
    try {
      const keys = keyRef.current;
      const next = await runtimeApi.breakupAction(view.sessionId, {
        ...body,
        ...(keys.apiKey ? { apiKey: keys.apiKey, apiBase: keys.apiBase, apiModel: keys.apiModel } : {}),
      });
      setView(next.data);
      if (next.data.ripple) {
        setNotice(String(next.data.ripple));
      }
    } catch (err) {
      setNotice(isApiError(err) ? err.msg : err instanceof Error ? err.message : "操作失败");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    const host = hostRef.current;
    if (!host || !view) {
      return;
    }
    host.innerHTML = ORIGINAL_MARKUP;
    host.querySelector("#modal-mask")?.classList.remove("show");
    host.querySelectorAll("template").forEach((tpl) => {
      const parent = tpl.parentElement;
      if (parent) {
        parent.appendChild(tpl.content.cloneNode(true));
      }
    });
    rewriteAssets(host, view.assets);
    renderWallpaper(host, view, payload);
    renderWall(
      host,
      view,
      (side) => void act({ type: "pick-side", side }),
      () => setNotice(String(view.copy.locked || "敬请期待")),
    );
    const screen = String(payload.screen || "screen-title");
    host.querySelectorAll("section.screen").forEach((section) => {
      section.classList.toggle("active", section.id === screen);
    });
    const trial = Boolean(view.trial);
    const demo = host.querySelector("#demo-badge") as HTMLElement | null;
    if (demo) {
      demo.hidden = !trial;
    }
    const chrome = host.querySelector("#app-chrome") as HTMLElement | null;
    const metrics = host.querySelector("#metric-board") as HTMLElement | null;
    const worldScreens = new Set([
      "screen-home",
      "screen-chat",
      "screen-moments",
      "screen-notes",
      "screen-wallet",
      "screen-photos",
      "screen-monitor",
    ]);
    const metricScreens = new Set(["screen-chat", "screen-notes", "screen-wallet", "screen-photos"]);
    const worldline = String(payload.worldline || "original");
    const worldChip = host.querySelector("#world-chip") as HTMLElement | null;
    if (worldChip) {
      worldChip.textContent = worldline === "rewritten" ? "回到原故事" : "切到改写版";
    }
    if (chrome) {
      if (screen === "screen-home" || !worldScreens.has(screen)) {
        chrome.hidden = true;
        if (metrics) {
          metrics.hidden = true;
        }
      } else if (screen === "screen-moments") {
        chrome.hidden = true;
        if (metrics) {
          metrics.hidden = true;
        }
        const tools = host.querySelector("#screen-moments .moments-nav-tools");
        if (tools && worldChip) {
          tools.appendChild(worldChip);
        }
        if (tools && demo) {
          tools.appendChild(demo);
        }
      } else {
        chrome.hidden = false;
        if (metrics) {
          metrics.hidden = !metricScreens.has(screen);
        }
        const header = host.querySelector(`#${screen} header`);
        if (header) {
          header.after(chrome);
        } else {
          host.querySelector(`#${screen}`)?.prepend(chrome);
        }
      }
    }
    const dockWorld = host.querySelector("#dock-world") as HTMLButtonElement | null;
    const dockLabel = host.querySelector("#dock-world-label");
    if (dockLabel) {
      dockLabel.textContent = worldline === "rewritten" ? "结局改写后" : "原世界回溯中";
    }
    if (dockWorld) {
      dockWorld.setAttribute("aria-pressed", worldline === "rewritten" ? "true" : "false");
      dockWorld.classList.toggle("is-on", worldline === "rewritten");
    }
    const bp = Number(payload.bp ?? 100);
    const lm = Number(payload.lm ?? 24);
    const lf = Number(payload.lf ?? 70);
    const setText = (id: string, text: string) => {
      const el = host.querySelector(id);
      if (el) {
        el.textContent = text;
      }
    };
    setText("#m-bp", `${bp}%`);
    setText("#h-bp", `${bp} %`);
    setText("#m-love", `${payload.side === "b" ? lf : lm}%`);
    setText("#h-lm", String(lm));
    setText("#h-lf", String(lf));
    setText("#wall-slide-type", view.wall.slides[0]?.typeTag ?? "");
    const gold = host.querySelector("#wall-tip-text");
    if (gold && !host.querySelector("#screen-wall.active")) {
      gold.textContent = view.wall.gold ?? view.copy.exampleEyebrow ?? "";
    }

    const couple = asRecord(view.couple);
    const sideWrap = host.querySelector("#side-wrap");
    if (sideWrap) {
      sideWrap.innerHTML = "";
      (["a", "b"] as const).forEach((side) => {
        const person = asRecord(couple[side]);
        const card = document.createElement("button");
        card.type = "button";
        card.className = "side-card";
        card.innerHTML = `<strong>${String(person.name ?? side)}</strong><p>${String(person.sidePick ?? person.brief ?? "")}</p>`;
        card.addEventListener("click", () => void act({ type: "pick-side", side }));
        sideWrap.appendChild(card);
      });
    }

    const rewritten = asRecord(payload.rewritten);
    const nodes = asList(rewritten.nodes).length ? asList(rewritten.nodes) : asList(couple.nodes);
    const playerSide = String(payload.side || "a");
    const other = asRecord(couple[playerSide === "b" ? "a" : "b"]);
    const chatTitle = host.querySelector("#chat-title");
    const chatLetter = host.querySelector("#chat-letter");
    if (chatTitle) {
      chatTitle.textContent = String(other.name || "私信");
    }
    if (chatLetter) {
      chatLetter.textContent = String(other.name || "?").slice(0, 1);
    }
    const thread = host.querySelector("#chat-thread");
    if (thread) {
      thread.innerHTML = "";
      nodes.forEach((node) => {
        const day = document.createElement("div");
        day.className = "day-label day-anchor";
        const date = node.date ? `${String(node.date)} · ` : "";
        day.textContent = `${date}${String(node.title || node.narr || "")}`;
        thread.appendChild(day);
        const lines = asList(node.chat);
        if (!lines.length) {
          const empty = document.createElement("div");
          empty.className = "mine-soft";
          empty.textContent = "这天没有聊天记录";
          thread.appendChild(empty);
          return;
        }
        lines.forEach((msg) => {
          const mine = String(msg.side || "") === playerSide;
          const row = document.createElement("div");
          row.className = `bubble-row ${mine ? "me" : "them"}`;
          const col = document.createElement("div");
          col.className = "bubble-col";
          const bubble = document.createElement("div");
          bubble.className = "bubble";
          if (msg.rewritten) {
            bubble.classList.add("rewritten");
          }
          if (msg.edit) {
            bubble.classList.add("tamper");
          }
          bubble.textContent = msg.recall ? "已撤回" : String(msg.text || "……");
          if (msg.edit && worldline === "rewritten") {
            bubble.classList.add("editable");
            bubble.addEventListener("click", () => {
              openRewriteModal(host, String(msg.text ?? ""), (next) => {
                void act({ type: "rewrite", nodeId: node.id, msgId: msg.id, text: next });
              });
            });
          }
          col.appendChild(bubble);
          if (msg.time) {
            const time = document.createElement("div");
            time.className = "bubble-time";
            time.textContent = String(msg.time);
            col.appendChild(time);
          }
          row.appendChild(col);
          thread.appendChild(row);
        });
      });
    }

    const moments = host.querySelector("#moments-body");
    if (moments) {
      moments.innerHTML = "";
      asList(rewritten.moments).forEach((item) => {
        const card = document.createElement("article");
        card.className = "moment-card";
        card.textContent = String(item.text ?? item.caption ?? "");
        moments.appendChild(card);
      });
    }
    const notes = host.querySelector("#notes-body");
    if (notes) {
      notes.innerHTML = "";
      const sided = asRecord(rewritten.notes);
      asList(sided.a).concat(asList(sided.b)).forEach((item) => {
        const card = document.createElement("article");
        card.className = "note-card";
        card.textContent = String(item.text ?? item.body ?? "");
        notes.appendChild(card);
      });
      asList(asRecord(payload.world).noteHints).forEach((hint) => {
        const card = document.createElement("article");
        card.className = "note-card";
        card.textContent = String(hint);
        notes.appendChild(card);
      });
    }
    const juan = asRecord(asRecord(asRecord(payload.world).threadPatches).juan);
    const threads = asList(view.phoneLife?.threads);
    if (threads[0] && juan.preview) {
      threads[0].preview = juan.preview;
    }

    const progressed = Boolean(payload.side);
    const titleHint = host.querySelector("#title-hint");
    const titleContinue = host.querySelector("#btn-title-continue") as HTMLButtonElement | null;
    if (titleHint) {
      titleHint.textContent = progressed ? "检测到存档。" : "尚无存档。开始后进入序幕。";
    }
    if (titleContinue) {
      titleContinue.hidden = !progressed;
    }
    let prologueIndex = 0;
    if (String(payload.screen || "") === "screen-prologue") {
      paintPrologue(host, prologueIndex);
    }

    const onClick = (selector: string, handler: (event: Event) => void) => {
      host.querySelectorAll(selector).forEach((el) => el.addEventListener("click", handler));
    };
    onClick("#btn-title-start", () => void act({ type: "set-screen", screen: "screen-prologue" }));
    onClick("#btn-title-continue", () => void act({ type: "set-screen", screen: payload.side ? "screen-home" : "screen-wall" }));
    onClick("#screen-prologue", () => {
      if (String(payload.screen || "") !== "screen-prologue" || prologueIndex >= PROLOGUE_BEATS.length - 1) {
        return;
      }
      prologueIndex += 1;
      paintPrologue(host, prologueIndex);
    });
    onClick("#prologue-pick", (event) => {
      event.stopPropagation();
      void act({ type: "set-screen", screen: "screen-wall" });
    });
    onClick("#btn-demo", () => void act({ type: "set-screen", screen: "screen-wall" }));
    onClick("#btn-enter", () => {
      const key = (host.querySelector("#api-key") as HTMLInputElement | null)?.value ?? "";
      keyRef.current = {
        apiKey: key,
        apiBase: (host.querySelector("#api-base") as HTMLInputElement | null)?.value ?? "",
        apiModel: (host.querySelector("#api-model") as HTMLInputElement | null)?.value ?? "",
      };
      void act({ type: "set-screen", screen: "screen-wall" });
    });
    onClick("#btn-title-settings", () => void act({ type: "set-screen", screen: "screen-launch" }));
    onClick("#hook-continue", () => void act({ type: "set-screen", screen: "screen-home" }));
    onClick("#side-back,#home-back", () => void act({ type: "set-screen", screen: "screen-wall" }));
    onClick("#wall-back", () => void act({ type: "set-screen", screen: "screen-title" }));
    onClick("[data-back='home']", () => void act({ type: "set-screen", screen: "screen-home" }));
    onClick("[data-app='chat']", () => void act({ type: "set-screen", screen: "screen-chat" }));
    onClick("[data-app='moments']", () => void act({ type: "set-screen", screen: "screen-moments" }));
    onClick("[data-app='notes']", () => void act({ type: "set-screen", screen: "screen-notes" }));
    onClick("[data-app='wallet']", () => void act({ type: "set-screen", screen: "screen-wallet" }));
    onClick("[data-app='photos']", () => void act({ type: "set-screen", screen: "screen-photos" }));
    onClick("[data-app='monitor']", () => void act({ type: "monitor" }));
    onClick("[data-app='settings']", () => void act({ type: "set-screen", screen: "screen-launch" }));
    onClick("#dock-world,#world-chip", () =>
      void act({ type: "set-worldline", worldline: worldline === "rewritten" ? "original" : "rewritten" }));
    onClick("#btn-release,#btn-book,#ending-book", () => void act({ type: "set-screen", screen: "screen-book" }));
    onClick("#book-back", () => void act({ type: "set-screen", screen: "screen-home" }));
    const bgm = host.querySelector("#home-bgm-audio") as HTMLAudioElement | null;
    const bgmUrl = view.assets["audio/home-bgm.mp3"];
    if (bgm && bgmUrl) {
      bgm.src = bgmUrl;
      bgm.loop = true;
    }
    onClick("#home-music-toggle", () => {
      if (!bgm || !bgmUrl) {
        setNotice("音频尚未配置");
        return;
      }
      const label = host.querySelector("#home-music-btn-label");
      const button = host.querySelector("#home-music-toggle");
      if (bgm.paused) {
        void bgm.play().then(() => {
          if (label) label.textContent = "Pause";
          button?.classList.remove("play");
          button?.classList.add("pause");
        }).catch(() => setNotice("无法播放：请检查音频链接或网络"));
      } else {
        bgm.pause();
        if (label) label.textContent = "Play";
        button?.classList.remove("pause");
        button?.classList.add("play");
      }
    });
    onClick("#btn-send", () => {
      const input = host.querySelector("#chat-input") as HTMLInputElement | null;
      const text = input?.value.trim() ?? "";
      if (!text) {
        return;
      }
      if (worldline !== "rewritten") {
        setNotice(String(view.copy.originalSend));
        return;
      }
      void act({ type: "send", text });
      if (input) {
        input.value = "";
      }
    });
    const sendHint = host.querySelector("#send-hint") as HTMLElement | null;
    if (sendHint) {
      sendHint.hidden = worldline === "rewritten";
      sendHint.textContent = String(view.copy.originalSend);
    }
    showPhoneToast(host, notice ?? "");
  }, [view, payload, busy, notice]);

  return (
    <div className="breakup-play">
      <style>{`
        html, body { overflow: auto !important; height: auto !important; }
        .breakup-play #app { position: relative; inset: auto; height: auto; min-height: 0; }
        .breakup-play { min-height: 70vh; display: flex; justify-content: center; }
        .breakup-play .phone-shell { transform: scale(0.92); transform-origin: top center; }
        #home-wallpaper img { width: 100%; height: 100%; object-fit: cover; display: block; }
        ${rewriteCssUrls(view?.css.fonts ?? "", view?.assets ?? {})}
        ${rewriteCssUrls(view?.css.app ?? "", view?.assets ?? {})}
        ${rewriteCssUrls(view?.css.v4 ?? "", view?.assets ?? {})}
      `}</style>
      {error ? <ErrorBanner error={error} onRetry={() => void load()} /> : null}
      {notice ? (
        <p className="kjx-banner" role="status">
          {notice}
        </p>
      ) : null}
      <div ref={hostRef} />
    </div>
  );
}

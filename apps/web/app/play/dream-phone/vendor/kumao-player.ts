/**
 * 酷猫播放器：画面用壳内 neo-*，播放用 HTMLAudioElement（与分手结局主屏 BGM 同路径）。
 * 曲库与音频二进制只在浏览器 IndexedDB；切页不停播，灵动岛 Live Activity 接管迷你控件。
 */
import {
  addLocalTracks,
  getLocalTrack,
  listLocalTracks,
  removeLocalTrack,
  type LocalTrackMeta,
} from "./local-music-store";

function fmt(sec: number) {
  if (!Number.isFinite(sec) || sec < 0) {
    return "0:00";
  }
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

function escapeHtml(text: string) {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

export type KumaoPlayer = {
  refresh: () => Promise<void>;
  setScreen: (screen: string) => void;
  destroy: () => void;
};

export function mountKumaoPlayer(root: HTMLElement): KumaoPlayer {
  const content = root.querySelector("#kumaoContent") as HTMLElement | null;
  const phoneEl = root.querySelector("#phoneScreen") as HTMLElement | null;
  const live = root.querySelector("#liveActivity") as HTMLElement | null;
  if (!content || !phoneEl) {
    return { refresh: async () => undefined, setScreen: () => undefined, destroy: () => undefined };
  }
  const phone = phoneEl;
  if (content.dataset.kumaoMounted === "1") {
    const existing = content as HTMLElement & {
      __kumaoRefresh?: () => Promise<void>;
      __kumaoSetScreen?: (screen: string) => void;
    };
    return {
      refresh: async () => {
        await existing.__kumaoRefresh?.();
      },
      setScreen: (screen) => existing.__kumaoSetScreen?.(screen),
      destroy: () => undefined,
    };
  }
  content.dataset.kumaoMounted = "1";

  // Audio lives on phoneScreen so page toggles never tear it down.
  let audio = phone.querySelector("#kumaoAudio") as HTMLAudioElement | null;
  if (!audio) {
    audio = document.createElement("audio");
    audio.id = "kumaoAudio";
    audio.preload = "metadata";
    phone.appendChild(audio);
  }

  // 结构对齐 v3 updateLiveActivity：紧凑=图标+波形；展开才有曲名/进度/三键

  content.innerHTML = `
    <div class="neo-player" id="kumaoNowPlaying">
      <div class="neo-vinyl-wrap" id="kumaoSeek">
        <div class="neo-ring" id="kumaoRing" style="--p:0"></div>
        <div class="neo-vinyl" id="kumaoVinyl"><div class="cover" id="kumaoCover"></div></div>
        <div class="neo-times"><span id="kumaoCur">0:00</span><span id="kumaoDur">0:00</span></div>
      </div>
      <div class="neo-title" id="kumaoTitle">导入本地歌曲</div>
      <div class="neo-mode-hint" id="kumaoHint">音频缓存在本机浏览器，不会上传到服务器</div>
      <div class="neo-controls">
        <button type="button" class="neo-btn" data-kumao="prev" aria-label="上一首"><i class="ico prev"></i></button>
        <button type="button" class="neo-btn play" data-kumao="toggle" aria-label="播放"><i class="ico playico" id="kumaoPlayIco">▶</i></button>
        <button type="button" class="neo-btn" data-kumao="next" aria-label="下一首"><i class="ico next"></i></button>
      </div>
      <div class="neo-vol">
        <span class="v-ico lo" aria-hidden="true"></span>
        <input type="range" id="kumaoVol" min="0" max="1" step="0.01" value="0.85" aria-label="音量" />
        <span class="v-ico hi" aria-hidden="true"></span>
      </div>
      <div class="neo-sub">
        <button type="button" class="neo-btn" data-kumao="loop" title="单曲循环" aria-label="单曲循环"><i class="ico repeat"></i></button>
        <button type="button" class="neo-btn" data-kumao="share" title="分享到聊天（占位）" aria-label="分享"><i class="ico share"></i></button>
      </div>
    </div>
    <div id="kumaoPlaylistPane" hidden>
      <ul class="neo-list" id="kumaoList"></ul>
      <label class="neo-upload" id="kumaoUploadLab" for="kumaoFileInput">点击导入本地音乐（mp3 / m4a / wav）</label>
      <input type="file" id="kumaoFileInput" accept="audio/*,.mp3,.m4a,.aac,.wav,.ogg,.flac" multiple hidden />
      <p class="neo-mode-hint" style="text-align:center;padding:4px 12px 12px;">曲目只缓存在本机，清除站点数据会一并删除。</p>
    </div>
  `;

  const ring = content.querySelector("#kumaoRing") as HTMLElement;
  const vinyl = content.querySelector("#kumaoVinyl") as HTMLElement;
  const titleEl = content.querySelector("#kumaoTitle") as HTMLElement;
  const hintEl = content.querySelector("#kumaoHint") as HTMLElement;
  const curEl = content.querySelector("#kumaoCur") as HTMLElement;
  const durEl = content.querySelector("#kumaoDur") as HTMLElement;
  const playIco = content.querySelector("#kumaoPlayIco") as HTMLElement;
  const listEl = content.querySelector("#kumaoList") as HTMLElement;
  const pane = content.querySelector("#kumaoPlaylistPane") as HTMLElement;
  const nowPlaying = content.querySelector("#kumaoNowPlaying") as HTMLElement;
  const fileInput = content.querySelector("#kumaoFileInput") as HTMLInputElement;
  const vol = content.querySelector("#kumaoVol") as HTMLInputElement;
  const navTitle = root.querySelector("#pageKumao .nav-title") as HTMLElement | null;
  const listBtn = root.querySelector("#pageKumao .neo-nav-btn.list") as HTMLElement | null;
  const desk = root.querySelector("#deskMusicWidget") as HTMLElement | null;
  const deskTitle = root.querySelector("#deskMusicTitle") as HTMLElement | null;
  const deskSub = root.querySelector("#deskMusicSub") as HTMLElement | null;
  const deskPlay = root.querySelector("#deskMusicPlay") as HTMLElement | null;
  let tracks: LocalTrackMeta[] = [];
  let index = 0;
  let currentId: string | null = null;
  let objectUrl: string | null = null;
  let loopOne = false;
  let seeking = false;
  let screen = "pageLauncher";
  let sessionActive = false;
  let liveExpanded = false;
  let announceTimer: number | null = null;

  function revokeUrl() {
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl);
      objectUrl = null;
    }
  }

  function isPlaying() {
    return !audio!.paused && !audio!.ended;
  }

  function syncDesk() {
    const track = tracks[index];
    if (deskTitle) {
      deskTitle.textContent = track?.title || "酷猫音乐";
    }
    if (deskSub) {
      deskSub.textContent = track
        ? isPlaying()
          ? "正在播放 · 本机缓存"
          : `${track.fileName || track.artist} · 已缓存本机`
        : "点此打开 · 导入歌曲后可播放";
    }
    desk?.classList.toggle("playing", isPlaying() && Boolean(track));
  }

  /** 对齐 v3：默认 126×36 紧凑态；expanded 才显示曲名/进度/三键，announce 后收回。 */
  function paintLiveActivity() {
    if (!live) {
      return;
    }
    const track = tracks[index];
    const active = sessionActive && Boolean(track);
    if (!active) {
      live.className = "live-activity";
      live.setAttribute("aria-hidden", "true");
      live.innerHTML = "";
      liveExpanded = false;
      phone.classList.remove("has-live", "has-live-exp");
      return;
    }
    const playing = isPlaying();
    const title = track!.title || "正在播放";
    const sub = playing ? track!.artist || "酷猫音乐" : `已暂停 · ${track!.fileName || track!.artist || "酷猫"}`;
    const dur = audio!.duration || 0;
    const cur = audio!.currentTime || 0;
    const pct = dur > 0 ? Math.min(100, (cur / dur) * 100) : 0;
    live.className = `live-activity show music${liveExpanded ? " expanded" : ""}${playing ? " playing" : ""}`;
    live.setAttribute("aria-hidden", "false");
    phone.classList.add("has-live");
    phone.classList.toggle("has-live-exp", liveExpanded);
    if (!liveExpanded) {
      // 紧凑态：左圆环进度 + 中间滚动曲名 + 右波形，不超出 126×36
      live.innerHTML = `
        <div class="la-row la-row-compact">
          <div class="la-icon-wrap" style="--la-p:${pct.toFixed(2)}" aria-hidden="true">
            <i class="la-ring"></i>
            <div class="la-icon music">🎵</div>
          </div>
          <div class="la-ticker" title="${escapeHtml(title)}">
            <div class="la-ticker-inner">
              <span>${escapeHtml(title)}</span>
              <span aria-hidden="true">${escapeHtml(title)}</span>
            </div>
          </div>
          <div class="la-meta"><span class="la-bars"><i></i><i></i><i></i></span></div>
        </div>`;
      return;
    }
    live.innerHTML = `
      <div class="la-row">
        <div class="la-icon music" aria-hidden="true">🎵</div>
        <div class="la-main">
          <div class="la-title">${escapeHtml(title)}</div>
          <div class="la-sub">${escapeHtml(sub)}</div>
        </div>
        <div class="la-meta"><span class="la-bars"><i></i><i></i><i></i></span></div>
        <button type="button" class="la-action la-close" data-la="dismiss" title="关闭" aria-label="关闭">✕</button>
      </div>
      <div class="la-progress"><i style="width:${pct}%"></i></div>
      <div class="la-foot">
        <div class="la-btns">
          <button type="button" class="la-action" data-la="prev" title="上一首" aria-label="上一首">‹‹</button>
          <button type="button" class="la-action la-play" data-la="toggle" title="播放/暂停" aria-label="播放/暂停">${playing ? "❚❚" : "▶"}</button>
          <button type="button" class="la-action" data-la="next" title="下一首" aria-label="下一首">››</button>
        </div>
      </div>`;
  }

  function announceLive(ms = 900) {
    liveExpanded = true;
    paintLiveActivity();
    if (announceTimer != null) {
      window.clearTimeout(announceTimer);
    }
    announceTimer = window.setTimeout(() => {
      announceTimer = null;
      liveExpanded = false;
      paintLiveActivity();
    }, ms);
  }

  function syncIsland() {
    paintLiveActivity();
  }

  function syncPlayUi() {
    const playing = isPlaying();
    playIco.textContent = playing ? "❚❚" : "▶";
    vinyl.classList.toggle("playing", playing);
    syncDesk();
    syncIsland();
  }

  function syncProgress() {
    const dur = audio!.duration || 0;
    const cur = audio!.currentTime || 0;
    const p = dur > 0 ? Math.min(100, (cur / dur) * 100) : 0;
    ring.style.setProperty("--p", String(p));
    curEl.textContent = fmt(cur);
    durEl.textContent = fmt(dur);
    if (live?.classList.contains("show")) {
      const wrap = live.querySelector(".la-icon-wrap") as HTMLElement | null;
      if (wrap) {
        wrap.style.setProperty("--la-p", p.toFixed(2));
      }
      const bar = live.querySelector(".la-progress > i") as HTMLElement | null;
      if (bar) {
        bar.style.width = `${p}%`;
      }
      const sub = live.querySelector(".la-sub");
      if (sub && tracks[index]) {
        sub.textContent = isPlaying()
          ? tracks[index].artist || "酷猫音乐"
          : `已暂停 · ${tracks[index].fileName || tracks[index].artist || "酷猫"}`;
      }
      live.classList.toggle("playing", isPlaying());
    }
  }

  async function loadIndex(i: number, autoplay: boolean) {
    if (!tracks.length) {
      sessionActive = false;
      currentId = null;
      revokeUrl();
      audio!.removeAttribute("src");
      audio!.load();
      titleEl.textContent = "导入本地歌曲";
      hintEl.textContent = "音频缓存在本机浏览器，不会上传到服务器";
      syncPlayUi();
      syncProgress();
      renderList();
      return;
    }
    index = ((i % tracks.length) + tracks.length) % tracks.length;
    const meta = tracks[index];
    if (currentId === meta.id && audio!.src) {
      sessionActive = true;
      titleEl.textContent = meta.title;
      hintEl.textContent = `${meta.fileName || meta.artist} · ${(meta.size / 1024 / 1024).toFixed(1)} MB · 仅本机`;
      renderList();
      syncProgress();
      if (autoplay && audio!.paused) {
        try {
          await audio!.play();
        } catch {
          hintEl.textContent = "无法自动播放，请再点一次播放";
        }
      }
      syncPlayUi();
      return;
    }
    const full = await getLocalTrack(meta.id);
    if (!full) {
      tracks = await listLocalTracks();
      currentId = null;
      return loadIndex(0, autoplay);
    }
    const keepTime = 0;
    const wasPlaying = isPlaying();
    revokeUrl();
    objectUrl = URL.createObjectURL(full.blob);
    audio!.src = objectUrl;
    audio!.loop = loopOne;
    currentId = meta.id;
    sessionActive = true;
    titleEl.textContent = meta.title;
    hintEl.textContent = `${meta.fileName || meta.artist} · ${(meta.size / 1024 / 1024).toFixed(1)} MB · 仅本机`;
    renderList();
    syncProgress();
    if (autoplay || wasPlaying) {
      try {
        audio!.currentTime = keepTime;
        await audio!.play();
      } catch {
        hintEl.textContent = "无法自动播放，请再点一次播放";
      }
    }
    syncPlayUi();
  }

  function renderList() {
    if (!tracks.length) {
      listEl.innerHTML = `<li style="justify-content:center;color:#6D81A1;">还没有本地歌曲</li>`;
      return;
    }
    listEl.innerHTML = tracks
      .map(
        (t, i) => `<li class="${i === index ? "playing-track" : ""}" data-track-i="${i}" role="button">
          <span style="flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(t.title)}
            <small style="display:block;color:#8a97a8;font-weight:400;">${escapeHtml(t.fileName || "")}</small>
          </span>
          <button type="button" data-del-track="${t.id}" aria-label="删除" style="border:none;background:transparent;color:#999;cursor:pointer;">删</button>
        </li>`,
      )
      .join("");
    listEl.querySelectorAll<HTMLElement>("[data-track-i]").forEach((el) => {
      el.addEventListener("click", (ev) => {
        if ((ev.target as HTMLElement).closest("[data-del-track]")) {
          return;
        }
        void loadIndex(Number(el.dataset.trackI || 0), true);
        showPlaylist(false);
      });
    });
    listEl.querySelectorAll<HTMLElement>("[data-del-track]").forEach((el) => {
      el.addEventListener("click", async (ev) => {
        ev.stopPropagation();
        const id = el.dataset.delTrack || "";
        const wasCurrent = tracks[index]?.id === id;
        await removeLocalTrack(id);
        tracks = await listLocalTracks();
        if (!tracks.length) {
          await loadIndex(0, false);
          return;
        }
        if (wasCurrent) {
          currentId = null;
          await loadIndex(Math.min(index, tracks.length - 1), isPlaying());
        } else {
          index = Math.max(0, tracks.findIndex((t) => t.id === currentId));
          renderList();
          syncIsland();
        }
      });
    });
  }

  function showPlaylist(on: boolean) {
    pane.hidden = !on;
    nowPlaying.hidden = on;
    listBtn?.classList.toggle("to-now", on);
    if (navTitle) {
      navTitle.textContent = on ? "本地歌单" : "正在播放";
    }
  }

  async function togglePlay() {
    if (!tracks.length) {
      showPlaylist(true);
      return;
    }
    if (audio!.paused) {
      try {
        await audio!.play();
        sessionActive = true;
      } catch {
        hintEl.textContent = "无法播放：请检查文件格式";
      }
    } else {
      audio!.pause();
    }
    syncPlayUi();
  }

  async function refresh() {
    const prevId = currentId;
    const keepPlaying = isPlaying();
    const keepTime = audio!.currentTime || 0;
    tracks = await listLocalTracks();
    if (!tracks.length) {
      if (sessionActive) {
        await loadIndex(0, false);
      } else {
        renderList();
        syncPlayUi();
      }
      return;
    }
    const next = prevId ? tracks.findIndex((t) => t.id === prevId) : index;
    index = next < 0 ? 0 : next;
    if (prevId && tracks[index]?.id === prevId && audio!.src) {
      // Same track already loaded — do not tear down playback on screen paint.
      titleEl.textContent = tracks[index].title;
      hintEl.textContent = `${tracks[index].fileName || tracks[index].artist} · 仅本机`;
      renderList();
      syncProgress();
      syncPlayUi();
      return;
    }
    await loadIndex(index, false);
    if (keepPlaying && audio!.src) {
      try {
        audio!.currentTime = keepTime;
        await audio!.play();
      } catch {
        /* ignore */
      }
      syncPlayUi();
    }
  }

  function setScreen(next: string) {
    const prev = screen;
    screen = next || "pageLauncher";
    const active = sessionActive && Boolean(tracks[index]);
    // 对齐 v3 announce：切离酷猫时展开一下，再收回 126 紧凑态
    if (active && prev === "pageKumao" && screen !== "pageKumao") {
      announceLive(900);
      return;
    }
    if (screen === "pageKumao" && liveExpanded) {
      liveExpanded = false;
      if (announceTimer != null) {
        window.clearTimeout(announceTimer);
        announceTimer = null;
      }
    }
    paintLiveActivity();
  }

  (content as HTMLElement & {
    __kumaoRefresh?: () => Promise<void>;
    __kumaoSetScreen?: (screen: string) => void;
  }).__kumaoRefresh = refresh;
  (content as HTMLElement & { __kumaoSetScreen?: (screen: string) => void }).__kumaoSetScreen = setScreen;

  content.querySelector('[data-kumao="toggle"]')?.addEventListener("click", () => void togglePlay());
  content.querySelector('[data-kumao="prev"]')?.addEventListener("click", () => void loadIndex(index - 1, true));
  content.querySelector('[data-kumao="next"]')?.addEventListener("click", () => void loadIndex(index + 1, true));
  content.querySelector('[data-kumao="loop"]')?.addEventListener("click", (ev) => {
    loopOne = !loopOne;
    audio!.loop = loopOne;
    (ev.currentTarget as HTMLElement).classList.toggle("on", loopOne);
  });
  vol.addEventListener("input", () => {
    audio!.volume = Number(vol.value);
  });
  audio.volume = Number(vol.value);

  fileInput.addEventListener("change", async () => {
    if (!fileInput.files?.length) {
      return;
    }
    const added = await addLocalTracks(fileInput.files);
    fileInput.value = "";
    if (!added.length) {
      hintEl.textContent = "未识别到音频文件";
      return;
    }
    tracks = await listLocalTracks();
    const first = tracks.findIndex((t) => t.id === added[0].id);
    currentId = null;
    await loadIndex(first < 0 ? 0 : first, true);
    showPlaylist(true);
  });

  listBtn?.addEventListener("click", () => {
    showPlaylist(pane.hidden);
  });

  deskPlay?.addEventListener("click", (ev) => {
    ev.preventDefault();
    ev.stopPropagation();
    void togglePlay();
  });

  live?.addEventListener("click", (ev) => {
    const target = ev.target as HTMLElement;
    if (target.closest(".la-action")) {
      const action = target.closest<HTMLElement>("[data-la]")?.dataset.la;
      ev.stopPropagation();
      if (action === "prev") {
        void loadIndex(index - 1, true);
      } else if (action === "next") {
        void loadIndex(index + 1, true);
      } else if (action === "toggle") {
        void togglePlay();
      } else if (action === "dismiss") {
        liveExpanded = false;
        if (announceTimer != null) {
          window.clearTimeout(announceTimer);
          announceTimer = null;
        }
        paintLiveActivity();
      }
      return;
    }
    // v3 toggleLiveActivity：紧凑 → 展开；展开再点 → 收回并进酷猫
    if (!sessionActive || !tracks[index]) {
      return;
    }
    if (liveExpanded) {
      liveExpanded = false;
      if (announceTimer != null) {
        window.clearTimeout(announceTimer);
        announceTimer = null;
      }
      paintLiveActivity();
      if (screen !== "pageKumao") {
        root.querySelector<HTMLElement>('[data-go="pageKumao"]')?.click();
      }
      return;
    }
    liveExpanded = true;
    if (announceTimer != null) {
      window.clearTimeout(announceTimer);
    }
    announceTimer = window.setTimeout(() => {
      announceTimer = null;
      liveExpanded = false;
      paintLiveActivity();
    }, 2600);
    paintLiveActivity();
  });

  audio.addEventListener("timeupdate", () => {
    if (!seeking) {
      syncProgress();
    }
  });
  audio.addEventListener("loadedmetadata", syncProgress);
  audio.addEventListener("play", () => {
    sessionActive = true;
    syncPlayUi();
  });
  audio.addEventListener("pause", syncPlayUi);
  audio.addEventListener("ended", () => {
    if (!loopOne) {
      void loadIndex(index + 1, true);
    }
  });

  const seek = content.querySelector("#kumaoSeek") as HTMLElement;
  const onSeek = (clientX: number, clientY: number) => {
    const rect = seek.getBoundingClientRect();
    const cx = rect.left + rect.width / 2;
    const cy = rect.top + rect.height / 2;
    let ang = (Math.atan2(clientY - cy, clientX - cx) * 180) / Math.PI + 90;
    if (ang < 0) {
      ang += 360;
    }
    const dur = audio!.duration || 0;
    if (dur > 0) {
      audio!.currentTime = (ang / 360) * dur;
      syncProgress();
    }
  };
  seek.addEventListener("pointerdown", (ev) => {
    seeking = true;
    seek.classList.add("is-seeking");
    seek.setPointerCapture(ev.pointerId);
    onSeek(ev.clientX, ev.clientY);
  });
  seek.addEventListener("pointermove", (ev) => {
    if (seeking) {
      onSeek(ev.clientX, ev.clientY);
    }
  });
  seek.addEventListener("pointerup", () => {
    seeking = false;
    seek.classList.remove("is-seeking");
  });

  void refresh();

  return {
    refresh,
    setScreen,
    destroy: () => {
      // 离开游玩页：停播并收起灵动岛，避免站外继续响
      if (announceTimer != null) {
        window.clearTimeout(announceTimer);
        announceTimer = null;
      }
      try {
        audio!.pause();
      } catch {
        /* ignore */
      }
      audio!.removeAttribute("src");
      audio!.load();
      revokeUrl();
      currentId = null;
      sessionActive = false;
      liveExpanded = false;
      if (live) {
        live.className = "live-activity";
        live.setAttribute("aria-hidden", "true");
        live.innerHTML = "";
        delete live.dataset.kumaoIsland;
      }
      phone.classList.remove("has-live", "has-live-exp");
      desk?.classList.remove("playing");
      delete content.dataset.kumaoMounted;
      audio?.remove();
    },
  };
}

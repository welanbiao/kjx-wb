"use client";

import { useEffect, useId, useRef, useState } from "react";
import {
  BIND_COPY,
  BIND_HINTS,
  HERO_ACTIONS,
  MEMBER_SAMPLE,
  MEMBER_SECTION,
  QUOTA_COPY,
  TASKS,
  UNLOCK_COPY,
  WORK_ENTRY_NOTE,
  WORK_TYPES,
} from "@/lib/design-preview/content";
import type { DirectionId } from "@/lib/design-preview/tokens";

export function MemberDesk({ direction }: { direction: DirectionId }) {
  const douyinId = useId();
  const monthsId = useId();
  const codeId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const [remaining, setRemaining] = useState<number>(MEMBER_SAMPLE.remainingCount);
  const [unlocked, setUnlocked] = useState<number>(MEMBER_SAMPLE.unlockedCount);
  const [unlockedWorkIds, setUnlockedWorkIds] = useState<Set<string>>(
    () => new Set(WORK_TYPES.filter((work) => work.entitlement === "unlocked").map((work) => work.id)),
  );
  const [pendingWork, setPendingWork] = useState<(typeof WORK_TYPES)[number] | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [bindMessage, setBindMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!pendingWork) {
      return;
    }

    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const focusTimer = window.setTimeout(() => confirmRef.current?.focus(), 0);

    function handleDialogKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setPendingWork(null);
        return;
      }
      if (event.key !== "Tab" || !dialogRef.current) {
        return;
      }

      const focusable = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>("button:not(:disabled), a[href], input:not(:disabled)"),
      );
      if (focusable.length === 0) {
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleDialogKeyDown);
    return () => {
      window.clearTimeout(focusTimer);
      document.removeEventListener("keydown", handleDialogKeyDown);
      previousFocus?.focus();
    };
  }, [pendingWork]);

  function requestUnlock(work: (typeof WORK_TYPES)[number]) {
    if (unlockedWorkIds.has(work.id)) {
      setMessage(`${work.enterLabel}（预览，不进入运行时）`);
      return;
    }
    setPendingWork(work);
  }

  function confirmUnlock() {
    if (!pendingWork) {
      return;
    }
    if (remaining <= 0) {
      setMessage("剩余可解锁数为 0，不能再解锁新作品");
      setPendingWork(null);
      return;
    }
    setRemaining((value) => value - 1);
    setUnlocked((value) => value + 1);
    setUnlockedWorkIds((value) => new Set(value).add(pendingWork.id));
    setMessage(`${UNLOCK_COPY.afterUnlock} ${remaining - 1}`);
    setPendingWork(null);
  }

  return (
    <>
      <section id="hero" className="dp-hero" aria-labelledby="hero-title">
        <div className="dp-wrap">
          {direction === "b" ? <p className="dp-masthead">koujunxin</p> : null}
          <div className="dp-hero-layout">
            <div>
              {direction === "a" ? <div className="dp-accent-bar" aria-hidden="true" /> : null}
              <p className="dp-kicker">会员制自营平台</p>
              <h1 id="hero-title" className="dp-title">
                叩君芯
              </h1>
              <p className="dp-lead">
                用户注册平台账号并绑定会员身份，用解锁额度解锁平台自研的 AI 文游 与 多轮对话式 UI
                布局。
              </p>
              <div className="dp-actions">
                <a className="dp-btn" href="#works">
                  {HERO_ACTIONS.browse}
                </a>
                <a className="dp-btn dp-btn-ghost" href="#member">
                  {HERO_ACTIONS.login}
                </a>
                <a className="dp-btn dp-btn-ghost" href="#member">
                  {HERO_ACTIONS.register}
                </a>
              </div>
            </div>
            {direction === "a" ? (
              <aside className="dp-hero-aside">
                <p className="dp-hint">{WORK_ENTRY_NOTE}</p>
                <p className="dp-hint">{QUOTA_COPY.formula}</p>
              </aside>
            ) : null}
            {direction === "b" ? (
              <div className="dp-hero-panel">
                <p className="dp-kicker">作品入口</p>
                <p className="dp-lead">{WORK_ENTRY_NOTE}</p>
                <a className="dp-btn" href="#works">
                  {HERO_ACTIONS.browse}
                </a>
              </div>
            ) : null}
            {direction === "c" ? (
              <div className="dp-ticket">
                <p className="dp-kicker dp-code">{MEMBER_SAMPLE.memberCode}</p>
                <h2>通行证尚未打孔完成</h2>
                <p className="dp-lead">绑定会员 → 核对额度 → 解锁作品 → 进入会话或游玩界面。</p>
                <div className="dp-actions">
                  <a className="dp-btn" href="#member">
                    填写会员信息
                  </a>
                  <a className="dp-btn dp-btn-ghost" href="#works">
                    {HERO_ACTIONS.browse}
                  </a>
                </div>
              </div>
            ) : null}
          </div>
        </div>
      </section>

      <section id="works" className="dp-section" aria-labelledby="works-title">
        <div className="dp-wrap">
          <h2 id="works-title">作品入口</h2>
          <p className="dp-lead">{WORK_ENTRY_NOTE}</p>
          <div className="dp-work-grid">
            {WORK_TYPES.map((work) => {
              const isUnlocked = unlockedWorkIds.has(work.id);
              return (
                <article key={work.id} className="dp-card">
                <p className="dp-kicker">{work.status}</p>
                <h3>{work.typeLabel}</h3>
                <p>运行时：{work.surface}</p>
                <div className="dp-meta">
                  {work.channels.map((channel) => (
                    <span key={channel} className="dp-chip">
                      {channel}
                    </span>
                  ))}
                  <span className="dp-chip">
                    {isUnlocked ? "已解锁" : "未解锁"}
                  </span>
                </div>
                <div className="dp-actions">
                  <button type="button" className="dp-btn" onClick={() => requestUnlock(work)}>
                    {isUnlocked ? work.enterLabel : "解锁"}
                  </button>
                  <button
                    type="button"
                    className="dp-btn dp-btn-ghost"
                    onClick={() => setMessage(UNLOCK_COPY.demoBody)}
                  >
                    {UNLOCK_COPY.demoMode}
                  </button>
                </div>
              </article>
              );
            })}
          </div>
          {message ? (
            <p className="dp-banner" role="status" style={{ marginTop: "1rem" }}>
              {message}
            </p>
          ) : null}
        </div>
      </section>

      <section id="member" className="dp-section" aria-labelledby="member-title">
        <div className="dp-wrap">
          <h2 id="member-title">{MEMBER_SECTION.title}</h2>
          <p className="dp-hint">{MEMBER_SECTION.loggedIn}</p>
          <dl className="dp-stats" style={{ marginTop: "1rem" }}>
            <div className="dp-stat">
              <dt>{QUOTA_COPY.available}</dt>
              <dd>{MEMBER_SAMPLE.availableCount}</dd>
            </div>
            <div className="dp-stat">
              <dt>{QUOTA_COPY.unlocked}</dt>
              <dd>{unlocked}</dd>
            </div>
            <div className="dp-stat">
              <dt>{QUOTA_COPY.remaining}</dt>
              <dd>{remaining}</dd>
            </div>
            <div className="dp-stat">
              <dt>{QUOTA_COPY.subscribedDays}</dt>
              <dd>{MEMBER_SAMPLE.subscribedDays}</dd>
            </div>
          </dl>
          <div className="dp-work-grid" style={{ marginTop: "1.25rem" }}>
            <article className="dp-card">
              <h3>{MEMBER_SECTION.account}</h3>
              <p>
                {MEMBER_SAMPLE.nickname} · {MEMBER_SAMPLE.phoneMasked}
              </p>
              <p className="dp-hint">
                会员状态 {MEMBER_SAMPLE.memberStatus} · 绑定 {MEMBER_SAMPLE.bindStatus}
              </p>
              <p className="dp-hint">
                {QUOTA_COPY.startAt} {MEMBER_SAMPLE.startAt}
              </p>
              <p className="dp-hint">
                {QUOTA_COPY.expireAt} {MEMBER_SAMPLE.expireAt}
              </p>
              <p className="dp-hint">{QUOTA_COPY.formula}</p>
              <p className="dp-hint">{QUOTA_COPY.floorNote}</p>
              <p className="dp-hint">{QUOTA_COPY.buyout}</p>
            </article>
            <article className="dp-card">
              <h3>{BIND_COPY.unboundTitle}</h3>
              <p className="dp-lead">{BIND_COPY.unboundBody}</p>
              <form
                className="dp-form"
                onSubmit={(event) => {
                  event.preventDefault();
                  setBindMessage("预览不写库。三要素核对由后端完成：抖音号 + 已订阅月数 + 会员编码。");
                }}
              >
                <div className="dp-field">
                  <label htmlFor={douyinId}>{BIND_COPY.douyin}</label>
                  <input id={douyinId} name="douyin" autoComplete="off" />
                  <p className="dp-hint">{BIND_HINTS.douyin}</p>
                </div>
                <div className="dp-field">
                  <label htmlFor={monthsId}>{BIND_COPY.months}</label>
                  <input id={monthsId} name="months" inputMode="numeric" autoComplete="off" />
                  <p className="dp-hint">{BIND_HINTS.months}</p>
                </div>
                <div className="dp-field">
                  <label htmlFor={codeId}>{BIND_COPY.memberCode}</label>
                  <input id={codeId} name="memberCode" autoComplete="off" />
                  <p className="dp-hint">{BIND_HINTS.memberCode}</p>
                </div>
                <div className="dp-actions">
                  <button className="dp-btn" type="submit">
                    {BIND_COPY.submit}
                  </button>
                  <button
                    className="dp-btn dp-btn-ghost"
                    type="button"
                    onClick={() => setBindMessage(BIND_COPY.appeal)}
                  >
                    {BIND_COPY.appeal}
                  </button>
                </div>
              </form>
              {bindMessage ? (
                <p className="dp-banner" role="status">
                  {bindMessage}
                </p>
              ) : null}
            </article>
          </div>
          <h3 style={{ marginTop: "1.5rem" }}>{MEMBER_SECTION.tasks}</h3>
          <ol className="dp-tasks">
            {TASKS.map((task) => (
              <li key={task.id} className={direction === "c" ? "dp-task" : "dp-card"}>
                <div>
                  <h4 style={{ margin: "0 0 0.35rem" }}>{task.title}</h4>
                  <p className="dp-hint">{task.detail}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {pendingWork ? (
        <div
          className="dp-dialog-backdrop"
          role="presentation"
          onClick={() => setPendingWork(null)}
        >
          <div
            ref={dialogRef}
            className="dp-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="unlock-title"
            aria-describedby="unlock-description"
            onClick={(event) => event.stopPropagation()}
          >
            <h2 id="unlock-title">{UNLOCK_COPY.confirmTitle}</h2>
            <p id="unlock-description">
              {pendingWork.typeLabel} · {UNLOCK_COPY.afterUnlock} {Math.max(remaining - 1, 0)}
            </p>
            <p className="dp-hint">{UNLOCK_COPY.needValid}</p>
            <p className="dp-hint">{UNLOCK_COPY.buyoutNote}</p>
            <div className="dp-dialog-actions">
              <button ref={confirmRef} type="button" className="dp-btn" onClick={confirmUnlock}>
                {UNLOCK_COPY.confirm}
              </button>
              <button
                type="button"
                className="dp-btn dp-btn-ghost"
                onClick={() => setPendingWork(null)}
              >
                {UNLOCK_COPY.cancel}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}

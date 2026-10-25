"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner, SuccessBanner } from "@/components/product/Banner";
import { Button } from "@/components/product/Button";
import { EmptyState, LoadingState } from "@/components/product/States";
import { PurchaseDialog } from "@/components/product/PurchaseDialog";
import { UnlockDialog } from "@/components/product/UnlockDialog";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";
import { ALREADY_UNLOCKED, isApiError } from "@/lib/errors";
import { useLoadOnMount } from "@/lib/load";
import type { AiSettingsView, MemberSnapshot, WorkDetail } from "@/lib/types";
import { decodeWorkName, decodeWorkSummary } from "@/lib/work-title";

type DetailPayload = {
  work: WorkDetail;
  member: MemberSnapshot | null;
  settings: AiSettingsView | null;
};

export default function WorkDetailPage() {
  const params = useParams<{ workCode: string }>();
  const router = useRouter();
  const workCode = params.workCode;
  const [success, setSuccess] = useState<string | null>(null);
  const [actionError, setActionError] = useState<unknown>();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [purchaseOpen, setPurchaseOpen] = useState(false);
  const [unlocking, setUnlocking] = useState(false);
  const [purchasing, setPurchasing] = useState(false);
  const [unlockedAfterBuy, setUnlockedAfterBuy] = useState(false);
  const [channel, setChannel] = useState<"saved" | "platform">("saved");
  const { state, reload } = useLoadOnMount(async () => {
    try {
      const workRes = await clientApi.work(workCode);
      let member: MemberSnapshot | null = null;
      let settings: AiSettingsView | null = null;
      try {
        member = (await clientApi.memberProfile()).data;
      } catch {
        member = null;
      }
      try {
        settings = (await clientApi.aiSettings()).data;
      } catch {
        settings = null;
      }
      return { work: workRes.data, member, settings };
    } catch (err) {
      if (isApiError(err) && err.httpStatus === 401) {
        router.replace(`/login?next=/works/${workCode}`);
      }
      throw err;
    }
  });

  const work = state.kind === "ok" ? state.data.work : null;
  const member = state.kind === "ok" ? state.data.member : null;
  const settings = state.kind === "ok" ? state.data.settings : null;
  const savedReady = Boolean(settings?.configured);

  async function confirmPurchase() {
    setPurchasing(true);
    setActionError(undefined);
    try {
      const result = await clientApi.purchase(workCode);
      setSuccess(result.code === ALREADY_UNLOCKED ? COPY.alreadyUnlocked : COPY.successPurchase);
      setPurchaseOpen(false);
      setUnlockedAfterBuy(true);
      await reload();
    } catch (err) {
      setActionError(err);
      setPurchaseOpen(false);
    } finally {
      setPurchasing(false);
    }
  }

  async function confirmUnlock() {
    setUnlocking(true);
    setActionError(undefined);
    try {
      const result = await clientApi.unlock(workCode);
      setSuccess(result.code === ALREADY_UNLOCKED ? COPY.alreadyUnlocked : COPY.successUnlock);
      setConfirmOpen(false);
      if (result.data.entryUrl) {
        router.push(result.data.entryUrl);
        return;
      }
      await reload();
    } catch (err) {
      setActionError(err);
      setConfirmOpen(false);
    } finally {
      setUnlocking(false);
    }
  }

  const workName = work ? decodeWorkName(work.workCode, work.name) : "";
  const workSummary = work ? decodeWorkSummary(work.workCode, work.summary) : "";
  const isUnlocked = Boolean(work?.unlocked || unlockedAfterBuy);
  const canEnter = Boolean(isUnlocked && (work?.entryUrl || `/runtime/${workCode}`));
  const isPagePurchase = work?.workCode === "breakup-editor" || work?.workCode === "dream-phone";
  const cannotUnlockNew = member?.status === "EXPIRED" || member?.status === "NOT_STARTED";

  return (
    <AppShell variant="task">
      <section className="kjx-section">
        <div className="kjx-wrap kjx-enter">
          {state.kind === "loading" ? <LoadingState /> : null}
          {state.kind === "error" ? <ErrorBanner error={state.error} onRetry={() => void reload()} /> : null}
          {actionError ? <ErrorBanner error={actionError} /> : null}
          {success ? <SuccessBanner>{success}</SuccessBanner> : null}
          {state.kind === "ok" && !work ? <EmptyState title="作品不存在" /> : null}
          {work ? (
            <>
              <article className="kjx-enter-story">
                <h1 className="kjx-title">{workName}</h1>
                <p className="kjx-enter-lead">{workSummary}</p>
                <p className="kjx-hint">进入前先选定这次游玩使用的接口。以后可以在自己的配置和平台中转之间切换。</p>
              </article>
              <aside className="kjx-enter-api">
                <h2>选择接口</h2>
                <button
                  type="button"
                  className={channel === "saved" ? "kjx-api-choice is-on" : "kjx-api-choice"}
                  onClick={() => setChannel("saved")}
                >
                  <strong>{savedReady ? settings?.model || "我的接口" : "尚未配置"}</strong>
                  <span>{savedReady ? settings?.baseUrl : "到设置里保存一条模型接口"}</span>
                </button>
                <button type="button" className="kjx-api-choice" disabled>
                  <strong>平台中转</strong>
                  <span>以后提供，当前不可选</span>
                </button>
                <div className="kjx-actions">
                  {isPagePurchase ? (
                    <Link className="kjx-btn kjx-btn-ghost" href={`/play/${work.workCode}?trial=1&api=${channel}`}>
                      {COPY.trial}
                    </Link>
                  ) : null}
                  {canEnter ? (
                    <Link
                      className="kjx-btn"
                      href={`${work.entryUrl || `/runtime/${work.workCode}`}?api=${channel}`}
                      aria-disabled={!savedReady || channel !== "saved"}
                      onClick={(event) => {
                        if (!savedReady || channel !== "saved") {
                          event.preventDefault();
                        }
                      }}
                    >
                      进入
                    </Link>
                  ) : isPagePurchase ? (
                    <Button onClick={() => setPurchaseOpen(true)}>{COPY.purchase}</Button>
                  ) : (
                    <Button onClick={() => setConfirmOpen(true)} disabled={cannotUnlockNew && !isUnlocked}>
                      {COPY.unlock}
                    </Button>
                  )}
                </div>
              </aside>
            </>
          ) : null}
        </div>
      </section>
      {purchaseOpen && work ? (
        <PurchaseDialog
          work={work}
          loading={purchasing}
          onConfirm={() => void confirmPurchase()}
          onCancel={() => setPurchaseOpen(false)}
        />
      ) : null}
      {confirmOpen && work ? (
        <UnlockDialog
          work={work}
          member={member}
          loading={unlocking}
          onConfirm={() => void confirmUnlock()}
          onCancel={() => setConfirmOpen(false)}
        />
      ) : null}
    </AppShell>
  );
}

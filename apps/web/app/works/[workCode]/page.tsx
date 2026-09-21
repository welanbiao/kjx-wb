"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner, SuccessBanner } from "@/components/product/Banner";
import { Button } from "@/components/product/Button";
import { EmptyState, LoadingState } from "@/components/product/States";
import { UnlockDialog } from "@/components/product/UnlockDialog";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";
import { ALREADY_UNLOCKED, isApiError } from "@/lib/errors";
import { workStatusLabel, workTypeLabel } from "@/lib/format";
import { useLoadOnMount } from "@/lib/load";
import type { MemberSnapshot, WorkDetail } from "@/lib/types";

type DetailPayload = {
  work: WorkDetail;
  member: MemberSnapshot | null;
};

export default function WorkDetailPage() {
  const params = useParams<{ workCode: string }>();
  const router = useRouter();
  const workCode = params.workCode;
  const [success, setSuccess] = useState<string | null>(null);
  const [actionError, setActionError] = useState<unknown>();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [unlocking, setUnlocking] = useState(false);
  const { state, reload } = useLoadOnMount(async () => {
    try {
      const workRes = await clientApi.work(workCode);
      let member: MemberSnapshot | null = null;
      try {
        member = (await clientApi.memberProfile()).data;
      } catch {
        member = null;
      }
      const payload: DetailPayload = { work: workRes.data, member };
      return payload;
    } catch (err) {
      if (isApiError(err) && err.httpStatus === 401) {
        router.replace(`/login?next=/works/${workCode}`);
      }
      throw err;
    }
  });

  const work = state.kind === "ok" ? state.data.work : null;
  const member = state.kind === "ok" ? state.data.member : null;

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

  const canEnter = Boolean(work?.unlocked && work.entryUrl);
  const cannotUnlockNew = member?.status === "EXPIRED" || member?.status === "NOT_STARTED";

  return (
    <AppShell variant="task">
      <section className="kjx-section">
        <div className="kjx-wrap">
          <p className="kjx-hint">
            <Link href="/works">← {COPY.works}</Link>
          </p>
          {state.kind === "loading" ? <LoadingState /> : null}
          {state.kind === "error" ? <ErrorBanner error={state.error} onRetry={() => void reload()} /> : null}
          {actionError ? <ErrorBanner error={actionError} /> : null}
          {success ? <SuccessBanner>{success}</SuccessBanner> : null}
          {state.kind === "ok" && !work ? <EmptyState title="作品不存在" /> : null}
          {work ? (
            <article className="kjx-card">
              <p className="kjx-kicker">{workStatusLabel(work.status)}</p>
              <h1 className="kjx-title">{work.name}</h1>
              <p className="kjx-lead">{work.summary}</p>
              <div className="kjx-meta">
                <span className="kjx-chip">{workTypeLabel(work.workType)}</span>
                <span className="kjx-chip">{work.unlocked ? "已解锁" : "未解锁"}</span>
                {(work.allowedChannels ?? []).map((channel) => (
                  <span key={channel} className="kjx-chip">
                    {channel}
                  </span>
                ))}
              </div>
              {cannotUnlockNew && !work.unlocked ? (
                <p className="kjx-hint">{member?.status === "NOT_STARTED" ? COPY.notStartedHint : COPY.expiredHint}</p>
              ) : null}
              <div className="kjx-actions">
                {canEnter ? (
                  <Link className="kjx-btn" href={work.entryUrl!}>
                    {work.workType === "game" ? "进入游戏游玩界面" : "进入会话界面"}
                  </Link>
                ) : (
                  <Button onClick={() => setConfirmOpen(true)} disabled={cannotUnlockNew && !work.unlocked}>
                    {COPY.unlock}
                  </Button>
                )}
                <Link className="kjx-btn kjx-btn-ghost" href="/unlocks">
                  {COPY.unlocks}
                </Link>
              </div>
            </article>
          ) : null}
        </div>
      </section>
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

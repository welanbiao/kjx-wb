"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner } from "@/components/product/Banner";
import { EmptyState, LoadingState } from "@/components/product/States";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";
import { isApiError } from "@/lib/errors";
import { formatApiTime, workStatusLabel } from "@/lib/format";
import { useLoadOnMount } from "@/lib/load";
import type { EntitlementItem, MemberSnapshot } from "@/lib/types";

type UnlocksPayload = {
  list: EntitlementItem[];
  member: MemberSnapshot | null;
};

export default function UnlocksPage() {
  const router = useRouter();
  const { state, reload } = useLoadOnMount(async () => {
    try {
      const [entitlements, memberRes] = await Promise.all([
        clientApi.entitlements(),
        clientApi.memberProfile().catch(() => ({ data: null })),
      ]);
      const payload: UnlocksPayload = {
        list: entitlements.data.list,
        member: memberRes.data,
      };
      return payload;
    } catch (err) {
      if (isApiError(err) && err.httpStatus === 401) {
        router.replace("/login?next=/unlocks");
      }
      throw err;
    }
  });
  const list = state.kind === "ok" ? state.data.list : [];
  const member = state.kind === "ok" ? state.data.member : null;

  return (
    <AppShell>
      <section className="kjx-section">
        <div className="kjx-wrap">
          <h1 className="kjx-title">{COPY.unlocks}</h1>
          {member?.status === "EXPIRED" ? <p className="kjx-lead">{COPY.expiredHint}</p> : null}
          {state.kind === "loading" ? <LoadingState /> : null}
          {state.kind === "error" ? <ErrorBanner error={state.error} onRetry={() => void reload()} /> : null}
          {state.kind === "ok" && list.length === 0 ? <EmptyState title={COPY.emptyUnlocks} /> : null}
          <div className="kjx-work-grid">
            {list.map((item) => (
              <article key={item.workCode} className="kjx-card">
                <p className="kjx-kicker">{workStatusLabel(item.workStatus)}</p>
                <h2>{item.name}</h2>
                <p className="kjx-hint">解锁于 {formatApiTime(item.unlockedAt)}</p>
                <p className="kjx-hint">来源 {item.source === "official_add" ? "官方发放" : "自行解锁"}</p>
                <div className="kjx-actions">
                  {item.entryUrl ? (
                    <Link className="kjx-btn" href={item.entryUrl}>
                      {COPY.enter}
                    </Link>
                  ) : (
                    <Link className="kjx-btn" href={`/works/${item.workCode}`}>
                      查看详情
                    </Link>
                  )}
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>
    </AppShell>
  );
}

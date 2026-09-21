"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner } from "@/components/product/Banner";
import { EmptyState, LoadingState } from "@/components/product/States";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";
import { isApiError } from "@/lib/errors";
import { workStatusLabel, workTypeLabel } from "@/lib/format";
import { useLoadOnMount } from "@/lib/load";
import type { WorkSummary } from "@/lib/types";

export default function WorksPage() {
  const router = useRouter();
  const { state, reload } = useLoadOnMount(async () => {
    try {
      const result = await clientApi.works();
      return result.data.list;
    } catch (err) {
      if (isApiError(err) && err.httpStatus === 401) {
        router.replace("/login?next=/works");
      }
      throw err;
    }
  });
  const list: WorkSummary[] = state.kind === "ok" ? state.data : [];

  return (
    <AppShell>
      <section className="kjx-section">
        <div className="kjx-wrap">
          <h1 className="kjx-title">{COPY.works}</h1>
          <p className="kjx-lead">{COPY.guestWorks}</p>
          {state.kind === "loading" ? <LoadingState /> : null}
          {state.kind === "error" ? <ErrorBanner error={state.error} onRetry={() => void reload()} /> : null}
          {state.kind === "ok" && list.length === 0 ? <EmptyState title={COPY.emptyWorks} /> : null}
          <div className="kjx-work-grid">
            {list.map((work) => (
              <article key={work.workCode} className="kjx-card">
                <p className="kjx-kicker">{workStatusLabel(work.status)}</p>
                <h2>{work.name}</h2>
                <p>{work.summary}</p>
                <div className="kjx-meta">
                  <span className="kjx-chip">{workTypeLabel(work.workType)}</span>
                  <span className="kjx-chip">{work.unlocked ? "已解锁" : "未解锁"}</span>
                </div>
                <div className="kjx-actions">
                  <Link className="kjx-btn" href={`/works/${work.workCode}`}>
                    查看详情
                  </Link>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>
    </AppShell>
  );
}

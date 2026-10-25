"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner } from "@/components/product/Banner";
import { EmptyState, LoadingState } from "@/components/product/States";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";
import { isApiError } from "@/lib/errors";
import { workStatusLabel, workTypeLabel } from "@/lib/format";
import { useLoadOnMount } from "@/lib/load";
import type { WorkSummary, WorkType } from "@/lib/types";
import { decodeWorkName, decodeWorkSummary } from "@/lib/work-title";

const FILTERS = [
  { id: "unlocked", label: "已解锁" },
  { id: "locked", label: "未解锁" },
  { id: "game", label: "AI 文游" },
  { id: "layout", label: "对话布局" },
] as const;

type FilterId = (typeof FILTERS)[number]["id"];

export default function WorksPage() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<FilterId[]>([]);
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
  const shown = useMemo(() => filterWorks(list, query, selected), [list, query, selected]);

  function toggle(id: FilterId) {
    setSelected((current) => {
      if (current.includes(id)) {
        return current.filter((item) => item !== id);
      }
      const next = current.filter((item) => {
        if (id === "unlocked") {
          return item !== "locked";
        }
        if (id === "locked") {
          return item !== "unlocked";
        }
        return true;
      });
      return [...next, id];
    });
  }

  return (
    <AppShell>
      <section className="kjx-section">
        <div className="kjx-wrap">
          <div className="kjx-filters" aria-label="筛选">
            <span className="kjx-filter-count">已选 {selected.length}</span>
            {FILTERS.map((filter) => {
              const on = selected.includes(filter.id);
              return (
                <button
                  key={filter.id}
                  type="button"
                  className={on ? "kjx-filter is-on" : "kjx-filter"}
                  aria-pressed={on}
                  onClick={() => toggle(filter.id)}
                >
                  <span className="kjx-filter-check" aria-hidden="true">
                    ✓
                  </span>
                  {filter.label}
                </button>
              );
            })}
          </div>
          <div className="kjx-work-head">
            <h1 className="kjx-title">{COPY.works}</h1>
            <input
              className="kjx-search"
              type="search"
              value={query}
              placeholder="搜索作品"
              aria-label="搜索作品"
              onChange={(event) => setQuery(event.target.value)}
            />
          </div>
          <p className="kjx-lead kjx-world-lead">{COPY.guestWorks}</p>
          {state.kind === "loading" ? <LoadingState /> : null}
          {state.kind === "error" ? <ErrorBanner error={state.error} onRetry={() => void reload()} /> : null}
          {state.kind === "ok" && shown.length === 0 ? <EmptyState title={COPY.emptyWorks} /> : null}
          <ol className="kjx-copy-list">
            {shown.map((work) => (
              <li key={work.workCode}>
                <article className="kjx-copy">
                  <p className="kjx-copy-meta">
                    <span>{workStatusLabel(work.status)}</span>
                  </p>
                  <h2>{decodeWorkName(work.workCode, work.name)}</h2>
                  <p className="kjx-copy-quote">{decodeWorkSummary(work.workCode, work.summary)}</p>
                  <div className="kjx-meta">
                    <span className="kjx-chip">{workTypeLabel(work.workType)}</span>
                    <span className="kjx-chip">{work.unlocked ? "已解锁" : "未解锁"}</span>
                  </div>
                  <div className="kjx-actions">
                    <Link className="kjx-btn kjx-btn-ghost" href={`/works/${work.workCode}`}>
                      查看详情
                    </Link>
                  </div>
                </article>
              </li>
            ))}
          </ol>
        </div>
      </section>
    </AppShell>
  );
}

function filterWorks(list: WorkSummary[], query: string, selected: FilterId[]) {
  const text = query.trim().toLowerCase();
  const types = selected.filter((item): item is WorkType => item === "game" || item === "layout");
  const unlock = selected.includes("unlocked") ? "unlocked" : selected.includes("locked") ? "locked" : null;
  return list.filter((work) => {
    const name = decodeWorkName(work.workCode, work.name).toLowerCase();
    const summary = decodeWorkSummary(work.workCode, work.summary).toLowerCase();
    if (text && !name.includes(text) && !summary.includes(text)) {
      return false;
    }
    if (unlock === "unlocked" && !work.unlocked) {
      return false;
    }
    if (unlock === "locked" && work.unlocked) {
      return false;
    }
    if (types.length > 0 && !types.includes(work.workType)) {
      return false;
    }
    return true;
  });
}

"use client";

import { useId, useMemo, useState, useTransition } from "react";
import {
  ADMIN_COLUMNS,
  ADMIN_COPY,
  ADMIN_FILTERS,
  ADMIN_ROWS,
  BIND_FILTERS,
  type AdminMemberRow,
} from "@/lib/design-preview/content";

type Banner =
  | { tone: "success"; text: string }
  | { tone: "error"; text: string }
  | { tone: "empty"; text: string }
  | { tone: "loading"; text: string }
  | null;

export function AdminDesk() {
  const searchId = useId();
  const reasonId = useId();
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<(typeof ADMIN_FILTERS)[number]["id"]>("all");
  const [bind, setBind] = useState<(typeof BIND_FILTERS)[number]["id"]>("all");
  const [applied, setApplied] = useState({ query: "", status: "all", bind: "all" });
  const [reason, setReason] = useState("");
  const [rejecting, setRejecting] = useState<string | null>(null);
  const [banner, setBanner] = useState<Banner>(null);
  const [pending, startTransition] = useTransition();

  const rows = useMemo(() => {
    return ADMIN_ROWS.filter((row) => {
      const hay = `${row.memberCode}${row.douyin}`.toLowerCase();
      const q = applied.query.trim().toLowerCase();
      const statusOk = applied.status === "all" || row.status === applied.status;
      const bindOk = applied.bind === "all" || row.bindStatus === applied.bind;
      return (!q || hay.includes(q)) && statusOk && bindOk;
    });
  }, [applied]);

  function runQuery() {
    setBanner({ tone: "loading", text: ADMIN_COPY.loading });
    startTransition(() => {
      setApplied({ query, status, bind });
      setBanner(null);
    });
  }

  function reset() {
    setQuery("");
    setStatus("all");
    setBind("all");
    setApplied({ query: "", status: "all", bind: "all" });
    setBanner(null);
    setRejecting(null);
    setReason("");
  }

  function approve(row: AdminMemberRow) {
    setRejecting(null);
    setBanner({ tone: "success", text: `${ADMIN_COPY.success}（${row.memberCode}）` });
  }

  function reject(row: AdminMemberRow) {
    if (!reason.trim()) {
      setRejecting(row.memberCode);
      setBanner({ tone: "error", text: ADMIN_COPY.error });
      return;
    }
    setBanner({
      tone: "success",
      text: `${ADMIN_COPY.success}：${row.memberCode} / ${ADMIN_COPY.reason}已记录`,
    });
    setRejecting(null);
    setReason("");
  }

  return (
    <section id="admin" className="dp-section" aria-labelledby="admin-title">
      <div className="dp-wrap">
        <h2 id="admin-title">{ADMIN_COPY.title}</h2>
        <p className="dp-lead">{ADMIN_COPY.subtitle}</p>
        <p className="dp-hint">{ADMIN_COPY.note}</p>
        <div className="dp-admin">
          <div className="dp-admin-bar">
            <span>叩君芯后台 · {ADMIN_COPY.role}</span>
            <span>{ADMIN_COPY.module}</span>
          </div>
          <div className="dp-admin-body">
            <nav className="dp-admin-side" aria-label="后台模块">
              <p>系统</p>
              <a href="#admin" aria-current="page">
                {ADMIN_COPY.pages}
              </a>
            </nav>
            <div className="dp-admin-main">
              <p className="dp-crumb">会员中心 / {ADMIN_COPY.title}</p>
              <div className="dp-admin-panel">
                <form
                  className="dp-filters"
                  onSubmit={(event) => {
                    event.preventDefault();
                    runQuery();
                  }}
                >
                  <div className="dp-field">
                    <label htmlFor={searchId}>{ADMIN_COPY.search}</label>
                    <input
                      id={searchId}
                      value={query}
                      onChange={(event) => setQuery(event.target.value)}
                      autoComplete="off"
                    />
                  </div>
                  <button className="dp-btn" type="submit" disabled={pending}>
                    {ADMIN_COPY.query}
                  </button>
                  <button className="dp-btn dp-btn-ghost" type="button" onClick={reset}>
                    {ADMIN_COPY.reset}
                  </button>
                </form>
                <div className="dp-filter-pills" role="group" aria-label="会员状态">
                  {ADMIN_FILTERS.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      aria-pressed={status === item.id}
                      onClick={() => setStatus(item.id)}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
                <div className="dp-filter-pills" role="group" aria-label="绑定状态">
                  {BIND_FILTERS.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      aria-pressed={bind === item.id}
                      onClick={() => setBind(item.id)}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
                {rejecting ? (
                  <div className="dp-field" style={{ marginBottom: "0.75rem" }}>
                    <label htmlFor={reasonId}>{ADMIN_COPY.reason}</label>
                    <input
                      id={reasonId}
                      value={reason}
                      onChange={(event) => setReason(event.target.value)}
                    />
                  </div>
                ) : null}
                {banner ? (
                  <p className="dp-banner" data-tone={banner.tone} role="status">
                    {banner.text}
                  </p>
                ) : null}
                {pending ? (
                  <p className="dp-banner" role="status">
                    {ADMIN_COPY.loading}
                  </p>
                ) : null}
                {!pending && rows.length === 0 ? (
                  <p className="dp-banner" role="status">
                    {ADMIN_COPY.empty}
                  </p>
                ) : (
                  <div className="dp-table-wrap">
                    <table className="dp-table">
                      <thead>
                        <tr>
                          {ADMIN_COLUMNS.map((col) => (
                            <th key={col.key} scope="col">
                              {col.label}
                            </th>
                          ))}
                          <th scope="col">操作</th>
                        </tr>
                      </thead>
                      <tbody>
                        {rows.map((row) => (
                          <tr key={row.memberCode}>
                            <td>{row.memberCode}</td>
                            <td>{row.douyin}</td>
                            <td>
                              <span className={`dp-tag dp-tag-${row.status}`}>{row.statusLabel}</span>
                            </td>
                            <td>{row.expireAt}</td>
                            <td>{row.subscribedDays}</td>
                            <td>{row.availableCount}</td>
                            <td>{row.unlockedCount}</td>
                            <td>{row.remainingCount}</td>
                            <td>
                              <span className={`dp-tag dp-tag-${row.bindStatus}`}>{row.bindLabel}</span>
                            </td>
                            <td>{row.workType}</td>
                            <td>
                              <div className="dp-row-actions">
                                <button type="button" data-kind="approve" onClick={() => approve(row)}>
                                  {ADMIN_COPY.approve}
                                </button>
                                <button type="button" data-kind="reject" onClick={() => reject(row)}>
                                  {ADMIN_COPY.reject}
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                <div className="dp-pager">
                  <span>共 {rows.length} 条</span>
                  <span>每页 10 条 · 第 1 页</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

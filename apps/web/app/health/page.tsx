"use client";

import Link from "next/link";
import { useState } from "react";
import { getApiBaseUrl, pingBackendHealth } from "@/lib/api";
import { TOKEN_STORAGE } from "@/lib/session";

type PingState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "ok"; status: number; body: string }
  | { kind: "error"; detail: string };

export default function HealthPage() {
  const [ping, setPing] = useState<PingState>({ kind: "idle" });

  async function onPing() {
    setPing({ kind: "loading" });
    try {
      const result = await pingBackendHealth();
      setPing({ kind: "ok", status: result.status, body: result.body });
    } catch (error) {
      setPing({
        kind: "error",
        detail: error instanceof Error ? error.message : "request failed",
      });
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col gap-6 bg-slate-50 px-6 py-16 text-slate-900">
      <Link href="/" className="text-sm text-slate-500">
        ← 返回首页
      </Link>
      <h1 className="text-3xl font-semibold">前台健康检查</h1>
      <dl className="grid gap-3 rounded-2xl border border-slate-200 bg-white p-6 text-sm">
        <div>
          <dt className="text-slate-500">应用</dt>
          <dd>{process.env.NEXT_PUBLIC_APP_NAME ?? "叩君芯"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">版本</dt>
          <dd>{process.env.NEXT_PUBLIC_APP_VERSION ?? "0.0.0"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">API Base</dt>
          <dd className="break-all">{getApiBaseUrl() || "(未配置 NEXT_PUBLIC_API_BASE_URL)"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">凭证存储</dt>
          <dd>{TOKEN_STORAGE}</dd>
        </div>
      </dl>
      <button
        type="button"
        onClick={() => void onPing()}
        className="w-fit rounded-full bg-slate-900 px-4 py-2 text-sm font-medium text-white"
      >
        探测 /actuator/health
      </button>
      {ping.kind === "loading" ? <p className="text-sm text-slate-500">请求中…</p> : null}
      {ping.kind === "ok" ? (
        <pre className="overflow-auto rounded-2xl bg-slate-950 p-4 text-xs text-slate-100">
          HTTP {ping.status}
          {"\n"}
          {ping.body}
        </pre>
      ) : null}
      {ping.kind === "error" ? (
        <p className="text-sm text-rose-600">后端暂不可达：{ping.detail}</p>
      ) : null}
    </main>
  );
}

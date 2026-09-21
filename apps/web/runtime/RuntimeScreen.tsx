"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ErrorBanner } from "@/components/product/Banner";
import { EmptyState, LoadingState } from "@/components/product/States";
import { isApiError } from "@/lib/errors";
import { useLoadOnMount } from "@/lib/load";
import { runtimeApi } from "@/runtime/api";
import { RUNTIME_COPY } from "@/runtime/copy";
import { FormARenderer } from "@/runtime/form-a/FormARenderer";
import { SandboxHost } from "@/runtime/sandbox/SandboxHost";
import type { SandboxBoot, SessionView, StageView } from "@/runtime/types";

type Payload = {
  session: SessionView;
  stage: StageView;
  sandbox: SandboxBoot | null;
};

export function RuntimeScreen({ workCode }: { workCode: string }) {
  const router = useRouter();
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState<"send" | "save" | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const { state, reload } = useLoadOnMount(async () => {
    try {
      const listed = await runtimeApi.listSessions(workCode);
      const existing = listed.data.find((item) => item.status === "ACTIVE") ?? listed.data[0];
      const session = existing ?? (await runtimeApi.createSession(workCode)).data;
      const stage = await runtimeApi.stage(session.sessionId, workCode);
      const sandbox =
        stage.data.form === "B" || stage.data.form === "C"
          ? (await runtimeApi.sandbox(session.sessionId, workCode)).data
          : null;
      const payload: Payload = { session, stage: stage.data, sandbox };
      return payload;
    } catch (error) {
      if (isApiError(error) && error.httpStatus === 401) {
        router.replace(`/login?next=/runtime/${encodeURIComponent(workCode)}`);
      }
      throw error;
    }
  });

  async function send() {
    if (state.kind !== "ok" || busy) {
      return;
    }
    setBusy("send");
    setNotice(null);
    try {
      await runtimeApi.apply(state.data.session.sessionId, workCode, "PATCH", state.data.session.schemaVersion, {
        lastUserMessage: message,
      });
      await runtimeApi.invokeTool({
        sessionId: state.data.session.sessionId,
        workCode,
        payloadJson: JSON.stringify({
          toolCode: "chat.send",
          toolVersion: "1",
          argumentsJson: JSON.stringify({ text: message }),
        }),
      });
      setMessage("");
      await reload();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "发送失败");
    } finally {
      setBusy(null);
    }
  }

  async function save() {
    if (state.kind !== "ok" || busy) {
      return;
    }
    setBusy("save");
    setNotice(null);
    try {
      await runtimeApi.save(state.data.session.sessionId, workCode);
      setNotice(RUNTIME_COPY.saved);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "保存失败");
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="kjx-section">
      <div className="kjx-wrap">
        <p className="kjx-kicker">{workCode}</p>
        <h1 className="kjx-title">{RUNTIME_COPY.title}</h1>
        <p className="kjx-lead">{RUNTIME_COPY.lead}</p>
        {state.kind === "loading" ? <LoadingState label={RUNTIME_COPY.loading} /> : null}
        {state.kind === "error" ? <ErrorBanner error={state.error} onRetry={() => void reload()} /> : null}
        {state.kind === "ok" && state.data.stage.gated ? (
          <EmptyState title={RUNTIME_COPY.gated} />
        ) : null}
        {state.kind === "ok" && !state.data.stage.gated && state.data.stage.form === "A" ? (
          <>
            <FormARenderer stage={state.data.stage} />
            <form
              className="kjx-runtime-composer"
              onSubmit={(event) => {
                event.preventDefault();
                void send();
              }}
            >
              <label className="kjx-field">
                <span>{RUNTIME_COPY.input}</span>
                <textarea
                  value={message}
                  onChange={(event) => setMessage(event.target.value)}
                  required
                  maxLength={500}
                />
              </label>
              <p className="kjx-hint">{RUNTIME_COPY.inputHint}</p>
              <div className="kjx-actions">
                <button className="kjx-btn" type="submit" disabled={busy !== null}>
                  {busy === "send" ? RUNTIME_COPY.sending : RUNTIME_COPY.send}
                </button>
                <button className="kjx-btn kjx-btn-ghost" type="button" onClick={() => void save()} disabled={busy !== null}>
                  {busy === "save" ? RUNTIME_COPY.saving : RUNTIME_COPY.save}
                </button>
              </div>
            </form>
            <p className="kjx-hint">{RUNTIME_COPY.toolHint}</p>
            <p className="kjx-hint">{RUNTIME_COPY.aigc}</p>
          </>
        ) : null}
        {state.kind === "ok" && !state.data.stage.gated && state.data.sandbox ? (
          <SandboxHost boot={state.data.sandbox} onError={(error) => setNotice(error instanceof Error ? error.message : "沙箱失败")} />
        ) : null}
        {notice ? <p className="kjx-banner" role="status">{notice}</p> : null}
        <div className="kjx-actions" style={{ marginTop: "1rem" }}>
          <Link className="kjx-btn kjx-btn-ghost" href="/unlocks">
            {RUNTIME_COPY.back}
          </Link>
        </div>
      </div>
    </section>
  );
}

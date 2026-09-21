"use client";

import { useEffect, useRef } from "react";
import { runtimeApi } from "@/runtime/api";
import { RUNTIME_COPY } from "@/runtime/copy";
import {
  IFRAME_SANDBOX,
  acceptSandboxMessage,
  hostReply,
  payloadLooksSafeForIframe,
} from "@/runtime/sandbox/guard";
import type { SandboxBoot } from "@/runtime/types";

type Props = {
  boot: SandboxBoot;
  onError: (error: unknown) => void;
};

export function SandboxHost({ boot, onError }: Props) {
  const frameRef = useRef<HTMLIFrameElement>(null);

  useEffect(() => {
    function onMessage(event: MessageEvent) {
      const accepted = acceptSandboxMessage({
        data: event.data as { type?: string; nonce?: string; method?: string; id?: string; payload?: unknown },
        origin: event.origin,
        expectedNonce: boot.nonce,
        allowedOrigin: boot.origin,
      });
      if (!accepted.ok) {
        return;
      }
      void runtimeApi
        .host({
          sessionId: boot.sessionId,
          workCode: boot.workCode,
          nonce: boot.nonce,
          iframeOrigin: event.origin || "null",
          method: accepted.method,
          payloadJson: JSON.stringify(accepted.payload ?? {}),
        })
        .then((res) => {
          let payload: unknown = {};
          try {
            payload = JSON.parse(res.data.payloadJson) as unknown;
          } catch {
            payload = { raw: res.data.payloadJson };
          }
          if (!payloadLooksSafeForIframe(payload)) {
            payload = { ok: false, reason: "redacted" };
          }
          frameRef.current?.contentWindow?.postMessage(
            hostReply(accepted.id, boot.nonce, res.data.ok, payload),
            event.origin === "null" ? "*" : event.origin,
          );
        })
        .catch(onError);
    }
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, [boot, onError]);

  return (
    <section>
      <p className="kjx-hint">{RUNTIME_COPY.sandboxHint}</p>
      <iframe
        ref={frameRef}
        className="kjx-runtime-iframe"
        title="作品沙箱"
        src={boot.iframeSrc}
        sandbox={IFRAME_SANDBOX}
        referrerPolicy="no-referrer"
        allow=""
      />
    </section>
  );
}

"use client";

import type { ReactNode } from "react";
import { COPY } from "@/lib/copy";
import { isApiError } from "@/lib/errors";

export function ErrorBanner({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const code = isApiError(error) ? error.code : undefined;
  const requestId = isApiError(error) ? error.requestId : undefined;
  const message = error instanceof Error ? error.message : "请求失败";
  return (
    <div className="kjx-banner" data-tone="error" role="alert">
      <p>{message}</p>
      {code !== undefined ? (
        <p className="kjx-hint">
          {COPY.errorCode} {code}
          {requestId ? ` · ${COPY.requestId} ${requestId}` : ""}
        </p>
      ) : requestId ? (
        <p className="kjx-hint">
          {COPY.requestId} {requestId}
        </p>
      ) : null}
      {onRetry ? (
        <button type="button" className="kjx-btn kjx-btn-ghost" onClick={onRetry}>
          {COPY.retry}
        </button>
      ) : null}
    </div>
  );
}

export function SuccessBanner({ children }: { children: ReactNode }) {
  return (
    <p className="kjx-banner" data-tone="success" role="status">
      {children}
    </p>
  );
}

export function StatusBanner({ children }: { children: ReactNode }) {
  return (
    <p className="kjx-banner" role="status">
      {children}
    </p>
  );
}

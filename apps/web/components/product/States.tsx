"use client";

import { COPY } from "@/lib/copy";

export function LoadingState({ label = COPY.loading }: { label?: string }) {
  return (
    <p className="kjx-banner" role="status" aria-live="polite">
      {label}
    </p>
  );
}

export function EmptyState({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className="kjx-banner" role="status">
      <p>{title}</p>
      {detail ? <p className="kjx-hint">{detail}</p> : null}
    </div>
  );
}

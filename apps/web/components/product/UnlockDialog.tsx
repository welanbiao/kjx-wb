"use client";

import { useEffect, useId, useRef } from "react";
import { Button } from "@/components/product/Button";
import { COPY } from "@/lib/copy";
import type { MemberSnapshot, WorkSummary } from "@/lib/types";

type Props = {
  work: WorkSummary;
  member: MemberSnapshot | null;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export function UnlockDialog({ work, member, loading, onConfirm, onCancel }: Props) {
  const titleId = useId();
  const descId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const timer = window.setTimeout(() => confirmRef.current?.focus(), 0);

    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onCancel();
        return;
      }
      if (event.key !== "Tab" || !dialogRef.current) {
        return;
      }
      const focusable = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>("button:not(:disabled), a[href], input:not(:disabled)"),
      );
      if (focusable.length === 0) {
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", onKey);
    return () => {
      window.clearTimeout(timer);
      document.removeEventListener("keydown", onKey);
      previous?.focus();
    };
  }, [onCancel]);

  return (
    <div className="kjx-dialog-backdrop" role="presentation" onClick={onCancel}>
      <div
        ref={dialogRef}
        className="kjx-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id={titleId}>{COPY.confirmTitle}</h2>
        <p id={descId}>
          {work.name} · {COPY.quotaRemain} {member ? member.remainCount : "—"}
        </p>
        <p className="kjx-hint">{COPY.confirmHint}</p>
        <p className="kjx-hint">{COPY.needValid}</p>
        <div className="kjx-dialog-actions">
          <Button ref={confirmRef} onClick={onConfirm} loading={loading}>
            {COPY.confirm}
          </Button>
          <Button variant="ghost" onClick={onCancel} disabled={loading}>
            {COPY.cancel}
          </Button>
        </div>
      </div>
    </div>
  );
}

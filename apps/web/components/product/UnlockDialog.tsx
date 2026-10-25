"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { Button } from "@/components/product/Button";
import { COPY } from "@/lib/copy";
import type { MemberSnapshot, WorkSummary } from "@/lib/types";
import { decodeWorkName } from "@/lib/work-title";

type Props = {
  work: WorkSummary;
  member: MemberSnapshot | null;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export function UnlockDialog({ work, member, loading, onConfirm, onCancel }: Props) {
  return (
    <Dialog.Root open onOpenChange={(open) => { if (!open && !loading) onCancel(); }}>
      <Dialog.Portal>
        <Dialog.Overlay className="kjx-dialog-backdrop" />
        <Dialog.Content className="kjx-dialog" aria-describedby="unlock-desc">
          <Dialog.Title>{COPY.confirmTitle}</Dialog.Title>
          <p id="unlock-desc">
            {decodeWorkName(work.workCode, work.name)} · {COPY.quotaRemain} {member ? member.remainCount : "—"}
          </p>
          <p className="kjx-hint">{COPY.confirmHint}</p>
          <p className="kjx-hint">{COPY.needValid}</p>
          <div className="kjx-dialog-actions">
            <Button onClick={onConfirm} loading={loading}>
              {COPY.confirm}
            </Button>
            <Button variant="ghost" onClick={onCancel} disabled={loading}>
              {COPY.cancel}
            </Button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

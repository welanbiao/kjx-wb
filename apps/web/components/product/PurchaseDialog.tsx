"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { Button } from "@/components/product/Button";
import { COPY } from "@/lib/copy";
import type { WorkSummary } from "@/lib/types";
import { decodeWorkName } from "@/lib/work-title";

type Props = {
  work: WorkSummary;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export function PurchaseDialog({ work, loading, onConfirm, onCancel }: Props) {
  return (
    <Dialog.Root open onOpenChange={(open) => { if (!open && !loading) onCancel(); }}>
      <Dialog.Portal>
        <Dialog.Overlay className="kjx-dialog-backdrop" />
        <Dialog.Content className="kjx-dialog" aria-describedby="purchase-desc">
          <Dialog.Title>{COPY.purchaseTitle}</Dialog.Title>
          <p id="purchase-desc">{decodeWorkName(work.workCode, work.name)}</p>
          <p className="kjx-hint">{COPY.purchaseHint}</p>
          <div className="kjx-dialog-actions">
            <Button onClick={onConfirm} loading={loading}>
              {COPY.confirmPurchase}
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

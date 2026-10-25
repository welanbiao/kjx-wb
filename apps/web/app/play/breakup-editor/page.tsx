"use client";

import { Suspense } from "react";
import { AppShell } from "@/components/product/AppShell";
import { BreakupPlayClient } from "./BreakupPlayClient";

export default function BreakupPlayPage() {
  return (
    <AppShell variant="task">
      <Suspense fallback={<p className="kjx-hint">加载分手结局修改器…</p>}>
        <BreakupPlayClient />
      </Suspense>
    </AppShell>
  );
}

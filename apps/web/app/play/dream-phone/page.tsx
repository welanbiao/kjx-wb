import fs from "node:fs";
import path from "node:path";
import { Suspense } from "react";
import { AppShell } from "@/components/product/AppShell";
import { DreamPhonePlayClient } from "./DreamPhonePlayClient";

export default function DreamPhonePlayPage() {
  const markup = fs.readFileSync(path.join(process.cwd(), "app/play/dream-phone/vendor/shell.html"), "utf8");
  return (
    <AppShell variant="task">
      <Suspense fallback={<p className="kjx-hint">加载梦女小手机…</p>}>
        <DreamPhonePlayClient markup={markup} />
      </Suspense>
    </AppShell>
  );
}

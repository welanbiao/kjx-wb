import { Suspense } from "react";
import fs from "node:fs";
import path from "node:path";
import { AppShell } from "@/components/product/AppShell";
import { BreakupPlayClient } from "@/app/play/breakup-editor/BreakupPlayClient";
import { DreamPhonePlayClient } from "@/app/play/dream-phone/DreamPhonePlayClient";
import { RuntimeScreen } from "@/runtime/RuntimeScreen";

export default async function RuntimePage({
  params,
}: {
  params: Promise<{ workCode: string }>;
}) {
  const { workCode } = await params;
  if (workCode === "breakup-editor") {
    return (
      <AppShell variant="task">
        <Suspense fallback={<p className="kjx-hint">加载分手结局修改器…</p>}>
          <BreakupPlayClient />
        </Suspense>
      </AppShell>
    );
  }
  if (workCode === "dream-phone") {
    const markup = fs.readFileSync(path.join(process.cwd(), "app/play/dream-phone/vendor/shell.html"), "utf8");
    return (
      <AppShell variant="task">
        <Suspense fallback={<p className="kjx-hint">加载梦女小手机…</p>}>
          <DreamPhonePlayClient markup={markup} />
        </Suspense>
      </AppShell>
    );
  }
  return (
    <AppShell variant="task">
      <section className="kjx-section">
        <div className="kjx-wrap">
          <p className="kjx-hint">游玩内容尚未接入。VIP 版先完成绑定与解锁，进入后不会下发真实作品。</p>
        </div>
      </section>
      <RuntimeScreen workCode={workCode} />
    </AppShell>
  );
}

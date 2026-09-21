import Link from "next/link";
import { AppShell } from "@/components/product/AppShell";
import { COPY } from "@/lib/copy";

export default async function RuntimePlaceholderPage({
  params,
}: {
  params: Promise<{ workCode: string }>;
}) {
  const { workCode } = await params;
  return (
    <AppShell variant="task">
      <section className="kjx-section">
        <div className="kjx-wrap">
          <h1 className="kjx-title">作品入口已打开</h1>
          <p className="kjx-lead">{COPY.runtimeReserved}</p>
          <p className="kjx-hint">workCode：{workCode}</p>
          <div className="kjx-actions">
            <Link className="kjx-btn" href="/unlocks">
              返回{COPY.unlocks}
            </Link>
            <Link className="kjx-btn kjx-btn-ghost" href={`/works/${workCode}`}>
              返回详情
            </Link>
          </div>
        </div>
      </section>
    </AppShell>
  );
}

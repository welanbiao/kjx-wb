import Link from "next/link";
import { COMPARE, DIRECTIONS, SKIP_TO_CONTENT } from "@/lib/design-preview/content";

const ORDER = ["a", "b", "c"] as const;

export default function DesignPreviewHubPage() {
  return (
    <div className="dp-hub">
      <a className="dp-skip" href="#directions">
        {SKIP_TO_CONTENT}
      </a>
      <main className="dp-wrap" style={{ paddingTop: "2.5rem" }}>
        <p className="dp-kicker">{COMPARE.kicker}</p>
        <h1>{COMPARE.title}</h1>
        <p className="dp-lead">{COMPARE.lead}</p>
        <aside className="dp-recommend">
          <p className="dp-kicker">{COMPARE.recommendedLabel}</p>
          <p>
            <strong>{COMPARE.recommendedName}</strong>
            {" — "}
            {COMPARE.recommendedWhy}
          </p>
        </aside>
        <p className="dp-hint">{COMPARE.a11y}</p>
        <section id="directions" className="dp-hub-grid" style={{ marginTop: "1.5rem" }}>
          {ORDER.map((id) => {
            const item = DIRECTIONS[id];
            return (
              <article key={id} className="dp-hub-card">
                <p className="dp-hub-meta">
                  方向 {id.toUpperCase()} · {item.sourceLabel} · {item.from}
                </p>
                <h2>{item.name}</h2>
                <p>
                  <strong>理念</strong>：{item.thesis}
                </p>
                <p>
                  <strong>适用</strong>：{item.fit}
                </p>
                <p>
                  <strong>取舍</strong>：{item.tradeoff}
                </p>
                <Link className="dp-btn" href={`/design-preview/${id}`}>
                  {COMPARE.open}
                </Link>
              </article>
            );
          })}
        </section>
      </main>
    </div>
  );
}

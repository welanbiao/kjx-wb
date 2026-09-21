"use client";

import Link from "next/link";
import { AdminDesk } from "@/components/design-preview/AdminDesk";
import { MemberDesk } from "@/components/design-preview/MemberDesk";
import {
  COMPARE,
  DIRECTIONS,
  NAV_ITEMS,
  PRODUCT_NAME,
  SKIP_TO_CONTENT,
} from "@/lib/design-preview/content";
import type { DirectionId } from "@/lib/design-preview/tokens";

export function DirectionPreview({ direction }: { direction: DirectionId }) {
  const meta = DIRECTIONS[direction];

  return (
    <div className={`dp dp-${direction}`}>
      {direction === "c" ? <div className="dp-curtain" aria-hidden="true" /> : null}
      <a className="dp-skip" href="#hero">
        {SKIP_TO_CONTENT}
      </a>
      <header className="dp-header dp-wrap">
        <Link className="dp-brand" href="/design-preview">
          <strong>{PRODUCT_NAME}</strong>
          <span className="dp-hint">{meta.name}</span>
        </Link>
        <nav className="dp-nav" aria-label="页面区块">
          {NAV_ITEMS.map((item) => (
            <a key={item.href} href={item.href}>
              {item.label}
            </a>
          ))}
        </nav>
        <div className="dp-header-actions">
          <Link className="dp-btn dp-btn-ghost" href="/design-preview">
            {COMPARE.back}
          </Link>
        </div>
      </header>
      <main>
        <MemberDesk direction={direction} />
        <AdminDesk />
      </main>
      <footer className="dp-footer dp-wrap">
        <p>
          {meta.name} · {meta.sourceLabel} · {meta.from}
        </p>
        <p className="dp-footnote">{meta.thesis}</p>
        <p className="dp-footnote">{meta.fit}</p>
        <p className="dp-footnote">取舍：{meta.tradeoff}</p>
      </footer>
    </div>
  );
}

"use client";

import type { MemberSnapshot } from "@/lib/types";
import { COPY } from "@/lib/copy";

export function QuotaStrip({ member }: { member: MemberSnapshot }) {
  return (
    <dl className="kjx-stats">
      <div className="kjx-stat">
        <dt>{COPY.quotaAvailable}</dt>
        <dd>{member.availableCount}</dd>
      </div>
      <div className="kjx-stat">
        <dt>{COPY.quotaUnlocked}</dt>
        <dd>{member.unlockedCount}</dd>
      </div>
      <div className="kjx-stat">
        <dt>{COPY.quotaRemain}</dt>
        <dd>{member.remainCount}</dd>
      </div>
      {typeof member.subscribedDays === "number" ? (
        <div className="kjx-stat">
          <dt>{COPY.subscribedDays}</dt>
          <dd>{member.subscribedDays}</dd>
        </div>
      ) : null}
    </dl>
  );
}

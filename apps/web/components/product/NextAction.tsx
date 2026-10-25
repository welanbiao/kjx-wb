"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { COPY } from "@/lib/copy";
import { useSessionPresence } from "@/lib/session-presence";
import type { AccountProfile } from "@/lib/types";

function memberStep(profile: AccountProfile): { href: string; label: string } {
  if (profile.bindStatus === 2) {
    return { href: "/works", label: COPY.browse };
  }
  return { href: "/me", label: COPY.fillBinding };
}

export function NextAction() {
  const pathname = usePathname();
  const { presence, profile } = useSessionPresence(pathname);

  if (presence === "unknown") {
    return null;
  }

  if (presence === "guest" || !profile) {
    return (
      <div className="kjx-actions">
        <Link className="kjx-btn" href="/login">
          {COPY.loginNow}
        </Link>
        <Link className="kjx-btn kjx-btn-ghost" href="/register">
          {COPY.registerNow}
        </Link>
      </div>
    );
  }

  const step = memberStep(profile);
  return (
    <div className="kjx-actions kjx-actions-single">
      <Link className="kjx-btn" href={step.href}>
        {step.label}
      </Link>
    </div>
  );
}

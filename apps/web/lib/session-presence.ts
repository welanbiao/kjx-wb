"use client";

import { useEffect, useState } from "react";
import { clientApi } from "@/lib/client-api";
import { isApiError } from "@/lib/errors";
import type { AccountProfile } from "@/lib/types";

export type SessionPresence = "unknown" | "guest" | "member";

export function loginHref(next: string): string {
  return `/login?next=${encodeURIComponent(next)}`;
}

export function useSessionPresence(refreshKey: string) {
  const [presence, setPresence] = useState<SessionPresence>("unknown");
  const [profile, setProfile] = useState<AccountProfile | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    let cancelled = false;
    void clientApi
      .profile()
      .then((res) => {
        if (cancelled) {
          return;
        }
        setProfile(res.data);
        setPresence("member");
        setError(null);
      })
      .catch((err: unknown) => {
        if (cancelled) {
          return;
        }
        if (isApiError(err) && err.httpStatus === 401) {
          setProfile(null);
          setPresence("guest");
          setError(null);
          return;
        }
        setError(err);
      });
    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  return { presence, profile, error, setProfile, setPresence };
}

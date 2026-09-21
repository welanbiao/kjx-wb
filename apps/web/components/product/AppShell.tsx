"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ErrorBanner } from "@/components/product/Banner";
import { clientApi } from "@/lib/client-api";
import { COPY, PRODUCT_NAME } from "@/lib/copy";
import { isApiError } from "@/lib/errors";
import type { AccountProfile } from "@/lib/types";

const NAV = [
  { href: "/works", label: COPY.works },
  { href: "/unlocks", label: COPY.unlocks },
  { href: "/me", label: COPY.profile },
] as const;

type Props = {
  children: React.ReactNode;
  variant?: "public" | "task";
};

export function AppShell({ children, variant = "public" }: Props) {
  const pathname = usePathname();
  const router = useRouter();
  const [profile, setProfile] = useState<AccountProfile | null>(null);
  const [authError, setAuthError] = useState<unknown>(null);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    void clientApi.csrf().catch(() => undefined);
    void clientApi
      .profile()
      .then((res) => {
        setProfile(res.data);
        setAuthError(null);
      })
      .catch((error: unknown) => {
        if (isApiError(error) && error.httpStatus === 401) {
          setProfile(null);
          return;
        }
        setAuthError(error);
      });
  }, [pathname]);

  async function onLogout() {
    setLoggingOut(true);
    try {
      await clientApi.logout();
      setProfile(null);
      router.push("/login");
    } catch (error) {
      setAuthError(error);
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <div className={`kjx-app ${variant === "task" ? "kjx-task" : ""}`.trim()}>
      {variant === "public" ? <div className="kjx-curtain" aria-hidden="true" /> : null}
      <a className="kjx-skip" href="#main">
        {COPY.skip}
      </a>
      <header className="kjx-header kjx-wrap">
        <Link className="kjx-brand" href="/">
          <Image
            src="/brand/koujunxin-logo.jpg"
            alt=""
            width={44}
            height={44}
            className="kjx-brand-mark"
          />
          <span>
            <strong>{PRODUCT_NAME}</strong>
            <span className="kjx-hint">分幕通行证</span>
          </span>
        </Link>
        <nav className="kjx-nav" aria-label="主导航">
          {NAV.map((item) => (
            <Link key={item.href} href={item.href} aria-current={pathname.startsWith(item.href) ? "page" : undefined}>
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="kjx-header-actions">
          {profile ? (
            <>
              <Link className="kjx-btn kjx-btn-ghost" href="/me">
                {profile.nickname || profile.phoneMask || COPY.profile}
              </Link>
              <button type="button" className="kjx-btn kjx-btn-ghost" onClick={() => void onLogout()} disabled={loggingOut}>
                {loggingOut ? "退出中…" : COPY.logout}
              </button>
            </>
          ) : (
            <>
              <Link className="kjx-btn kjx-btn-ghost" href="/login">
                {COPY.login}
              </Link>
              <Link className="kjx-btn" href="/register">
                {COPY.register}
              </Link>
            </>
          )}
        </div>
      </header>
      <main id="main">{children}</main>
      {authError ? (
        <div className="kjx-wrap" style={{ paddingBottom: "1rem" }}>
          <ErrorBanner error={authError} />
        </div>
      ) : null}
      <footer className="kjx-footer kjx-wrap">
        <p>晴空海蓝 · 暖橙光 · 奶油米白</p>
        <p className="kjx-hint">额度与会员状态以后端返回值为准，前端不换算月数或天数。</p>
      </footer>
    </div>
  );
}

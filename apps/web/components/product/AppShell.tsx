"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ErrorBanner } from "@/components/product/Banner";
import { ADMIN_SESSION_KEY, hasAdminDeskSession } from "@/lib/admin-presence";
import { clientApi } from "@/lib/client-api";
import { COPY, PRODUCT_NAME } from "@/lib/copy";
import { loginHref, useSessionPresence } from "@/lib/session-presence";

const PATH = [
  { key: "home", href: "/", label: COPY.home, gated: false },
  { key: "works", href: "/works", label: COPY.works, gated: true },
  { key: "profile", href: "/me", label: COPY.profile, gated: true },
  { key: "settings", href: "/settings", label: "设置", gated: true },
  { key: "help", href: "/help", label: "帮助", gated: true },
] as const;

function currentStep(pathname: string): (typeof PATH)[number]["key"] | null {
  if (pathname === "/") {
    return "home";
  }
  if (pathname.startsWith("/works") || pathname.startsWith("/runtime") || pathname.startsWith("/play")) {
    return "works";
  }
  if (pathname.startsWith("/me")) {
    return "profile";
  }
  if (pathname.startsWith("/settings")) {
    return "settings";
  }
  if (pathname.startsWith("/help")) {
    return "help";
  }
  return null;
}

type Props = {
  children: React.ReactNode;
  variant?: "public" | "task";
};

export function AppShell({ children, variant = "public" }: Props) {
  const pathname = usePathname();
  const router = useRouter();
  const { presence, profile, error: authError, setProfile, setPresence } = useSessionPresence(pathname);
  const [logoutError, setLogoutError] = useState<unknown>(null);
  const [loggingOut, setLoggingOut] = useState(false);
  const [adminDesk, setAdminDesk] = useState(false);
  const step = currentStep(pathname);

  useEffect(() => {
    setAdminDesk(hasAdminDeskSession(window.sessionStorage.getItem(ADMIN_SESSION_KEY)));
    void clientApi.csrf().catch(() => undefined);
  }, [pathname]);

  async function onLogout() {
    setLoggingOut(true);
    try {
      await clientApi.logout();
      setProfile(null);
      setPresence("guest");
      router.push("/");
    } catch (error) {
      setLogoutError(error);
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <div className={`kjx-app ${variant === "task" ? "kjx-task" : ""}`.trim()}>
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
            <span className="kjx-hint">{COPY.worldKicker}</span>
          </span>
        </Link>
        <div className="kjx-header-nav">
          <nav aria-label="会员路径">
            <ol className="kjx-path">
              {PATH.map((item) => {
                const href = presence === "guest" && item.gated ? loginHref(item.href) : item.href;
                return (
                  <li key={item.key}>
                    <Link href={href} aria-current={step === item.key ? "step" : undefined}>
                      {item.label}
                    </Link>
                  </li>
                );
              })}
              {adminDesk ? (
                <li>
                  <a href="/admin/">管理</a>
                </li>
              ) : null}
            </ol>
          </nav>
        </div>
        <div className="kjx-header-actions">
          {profile ? (
            <>
              <p className="kjx-session">
                <Link className="kjx-session-name" href="/me">
                  {profile.nickname || profile.phoneMask || COPY.profile}
                </Link>
              </p>
              <button type="button" className="kjx-btn kjx-btn-ghost" onClick={() => void onLogout()} disabled={loggingOut}>
                {loggingOut ? "退出中…" : COPY.logout}
              </button>
            </>
          ) : presence === "guest" ? (
            <>
              <Link className="kjx-btn kjx-btn-ghost" href="/login">
                {COPY.loginNow}
              </Link>
              <Link className="kjx-btn kjx-btn-ghost" href="/register">
                {COPY.registerNow}
              </Link>
            </>
          ) : null}
        </div>
      </header>
      <main id="main" className={variant === "task" ? "kjx-sheet" : "kjx-world-main"}>
        {authError || logoutError ? (
          <div className="kjx-wrap" style={{ paddingTop: "1rem" }}>
            <ErrorBanner error={logoutError ?? authError} />
          </div>
        ) : null}
        {children}
      </main>
      <footer className="kjx-footer kjx-wrap">
        <p>叩君芯 · {COPY.worldKicker}</p>
        <p className="kjx-hint">额度与会员状态以后端返回值为准，前端不换算月数或天数。</p>
      </footer>
    </div>
  );
}

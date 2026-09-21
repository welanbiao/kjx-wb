"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, KeyboardEvent, Suspense, useState } from "react";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner, SuccessBanner } from "@/components/product/Banner";
import { Button } from "@/components/product/Button";
import { CaptchaField } from "@/components/product/CaptchaField";
import { Field, TextInput } from "@/components/product/Field";
import type { FrozenCaptcha } from "@/lib/captcha/types";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";

function LoginForm() {
  const router = useRouter();
  const search = useSearchParams();
  const next = search.get("next") || "/me";
  const [mode, setMode] = useState<"password" | "code">("password");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [captcha, setCaptcha] = useState<FrozenCaptcha | null>(null);
  const [captchaEpoch, setCaptchaEpoch] = useState(0);
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [success, setSuccess] = useState<string | null>(null);

  async function sendCode() {
    if (!captcha) {
      setError(new Error(COPY.captchaNeed));
      return;
    }
    setSending(true);
    setError(null);
    try {
      await clientApi.sendCode({ phone, scene: "login", captchaId: captcha.captchaId, captchaData: captcha.captchaData });
      setSuccess(COPY.codeSent);
    } catch (err) {
      setError(err);
    } finally {
      setSending(false);
      setCaptchaEpoch((value) => value + 1);
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      if (mode === "password") {
        await clientApi.login({ phone, password });
      } else {
        await clientApi.loginByCode({ phone, code });
      }
      setSuccess(COPY.successLogin);
      router.push(next);
      router.refresh();
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }

  function onTabKey(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
      event.preventDefault();
      setMode((current) => (current === "password" ? "code" : "password"));
    }
  }

  return (
    <div className="kjx-auth">
      <figure className="kjx-brand-art">
        <Image src="/brand/koujunxin-logo.jpg" alt="叩君芯图标" width={256} height={256} />
      </figure>
      <h1 className="kjx-title">{COPY.login}</h1>
      <div className="kjx-tabs" role="tablist" aria-label="登录方式" onKeyDown={onTabKey}>
        <button
          type="button"
          role="tab"
          id="tab-password"
          aria-controls="login-panel"
          aria-selected={mode === "password"}
          tabIndex={mode === "password" ? 0 : -1}
          onClick={() => setMode("password")}
        >
          {COPY.passwordLogin}
        </button>
        <button
          type="button"
          role="tab"
          id="tab-code"
          aria-controls="login-panel"
          aria-selected={mode === "code"}
          tabIndex={mode === "code" ? 0 : -1}
          onClick={() => setMode("code")}
        >
          {COPY.codeLogin}
        </button>
      </div>
      <form id="login-panel" role="tabpanel" aria-labelledby={mode === "password" ? "tab-password" : "tab-code"} className="kjx-form kjx-card" onSubmit={onSubmit}>
        <Field id="login-phone" label={COPY.phone}>
          <TextInput
            id="login-phone"
            name="phone"
            inputMode="numeric"
            autoComplete="username"
            required
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
          />
        </Field>
        {mode === "password" ? (
          <Field id="login-password" label={COPY.password}>
            <TextInput
              id="login-password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </Field>
        ) : (
          <>
            <CaptchaField value={captcha} onChange={setCaptcha} disabled={sending} refreshToken={captchaEpoch} />
            <Field id="login-code" label={COPY.smsCode}>
              <TextInput
                id="login-code"
                name="code"
                inputMode="numeric"
                required
                value={code}
                onChange={(event) => setCode(event.target.value)}
              />
            </Field>
            <Button
              type="button"
              variant="ghost"
              onClick={() => void sendCode()}
              loading={sending}
              pendingLabel={COPY.sendingCode}
              disabled={!captcha}
            >
              {COPY.sendCode}
            </Button>
          </>
        )}
        {error ? <ErrorBanner error={error} /> : null}
        {success ? <SuccessBanner>{success}</SuccessBanner> : null}
        <div className="kjx-actions">
          <Button type="submit" loading={loading}>
            {COPY.login}
          </Button>
          <Link className="kjx-btn kjx-btn-ghost" href="/reset-password">
            {COPY.forgotPassword}
          </Link>
        </div>
        <p className="kjx-hint">
          还没有账号？<Link href="/register">去注册</Link>
        </p>
      </form>
    </div>
  );
}

export default function LoginPage() {
  return (
    <AppShell variant="task">
      <Suspense fallback={<p className="kjx-wrap">{COPY.loading}</p>}>
        <LoginForm />
      </Suspense>
    </AppShell>
  );
}

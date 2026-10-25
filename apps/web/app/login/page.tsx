"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useState } from "react";
import * as Tabs from "@radix-ui/react-tabs";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner, SuccessBanner } from "@/components/product/Banner";
import { Button } from "@/components/product/Button";
import { CaptchaGate } from "@/components/product/CaptchaGate";
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
  const [captchaByMode, setCaptchaByMode] = useState<{ password: FrozenCaptcha | null; code: FrozenCaptcha | null }>({
    password: null,
    code: null,
  });
  const [captchaEpoch, setCaptchaEpoch] = useState({ password: 0, code: 0 });
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [desk, setDesk] = useState(false);

  function setModeCaptcha(target: "password" | "code", value: FrozenCaptcha | null) {
    setCaptchaByMode((current) => ({ ...current, [target]: value }));
  }

  async function sendCode() {
    if (!phone.trim()) {
      setError(new Error(COPY.phoneNeed));
      return;
    }
    const solved = captchaByMode.code;
    if (!solved) {
      setError(new Error(COPY.captchaNeed));
      return;
    }
    setSending(true);
    setError(null);
    try {
      await clientApi.sendCode({ phone, scene: "login", captchaId: solved.captchaId, captchaData: solved.captchaData });
      setSuccess(COPY.codeSent);
    } catch (err) {
      setError(err);
    } finally {
      setSending(false);
      setCaptchaByMode((current) => ({ ...current, code: null }));
      setCaptchaEpoch((current) => ({ ...current, code: current.code + 1 }));
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (mode === "password" && !captchaByMode.password) {
      setError(new Error(COPY.captchaNeed));
      return;
    }
    setLoading(true);
    setError(null);
    try {
      if (mode === "password") {
        const solved = captchaByMode.password;
        if (!solved) {
          setError(new Error(COPY.captchaNeed));
          return;
        }
        await clientApi.login({
          phone,
          password,
          captchaId: solved.captchaId,
          captchaData: solved.captchaData,
        });
      } else {
        await clientApi.loginByCode({ phone, code });
      }
      setSuccess(COPY.successLogin);
      router.push(next);
      router.refresh();
    } catch (err) {
      setError(err);
      if (mode === "password") {
        setCaptchaByMode((current) => ({ ...current, password: null }));
        setCaptchaEpoch((current) => ({ ...current, password: current.password + 1 }));
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="kjx-auth">
      <h1 className="kjx-title">{COPY.login}</h1>
      {desk ? (
        <div className="kjx-form kjx-card">
          <iframe className="kjx-admin-login-frame" title="管理台登录" src="/admin/login?embed=1" />
          <div className="kjx-actions">
            <button type="button" className="kjx-btn kjx-btn-ghost" onClick={() => setDesk(false)}>
              会员登录
            </button>
          </div>
        </div>
      ) : (
        <Tabs.Root
          value={mode}
          onValueChange={(value) => {
            const next = value as "password" | "code";
            setMode(next);
            setCaptchaByMode({ password: null, code: null });
            setCaptchaEpoch((current) => ({
              password: current.password + 1,
              code: current.code + 1,
            }));
            setError(null);
            setSuccess(null);
          }}
        >
          <Tabs.List className="kjx-tabs" aria-label="登录方式">
            <Tabs.Trigger className="kjx-tab" value="password">
              {COPY.passwordLogin}
            </Tabs.Trigger>
            <Tabs.Trigger className="kjx-tab" value="code">
              {COPY.codeLogin}
            </Tabs.Trigger>
          </Tabs.List>
          <form className="kjx-form kjx-card" onSubmit={onSubmit}>
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
            <Tabs.Content className="kjx-tab-panel" value="password">
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
              <CaptchaGate
                value={captchaByMode.password}
                onChange={(value) => setModeCaptcha("password", value)}
                disabled={loading}
                refreshToken={captchaEpoch.password}
              />
            </Tabs.Content>
            <Tabs.Content className="kjx-tab-panel" value="code">
              <CaptchaGate
                value={captchaByMode.code}
                onChange={(value) => setModeCaptcha("code", value)}
                disabled={sending}
                refreshToken={captchaEpoch.code}
              />
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
                disabled={!phone.trim() || !captchaByMode.code}
              >
                {COPY.sendCode}
              </Button>
            </Tabs.Content>
            {error ? <ErrorBanner error={error} /> : null}
            {success ? <SuccessBanner>{success}</SuccessBanner> : null}
            <div className="kjx-actions">
              <Button type="submit" loading={loading} disabled={mode === "password" && !captchaByMode.password}>
                {COPY.login}
              </Button>
              <Link className="kjx-btn kjx-btn-ghost" href="/reset-password">
                {COPY.forgotPassword}
              </Link>
              <button type="button" className="kjx-btn kjx-btn-ghost" onClick={() => setDesk(true)}>
                管理台登录
              </button>
            </div>
            <p className="kjx-hint">
              还没有账号？<Link href="/register">去注册</Link>
            </p>
          </form>
        </Tabs.Root>
      )}
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


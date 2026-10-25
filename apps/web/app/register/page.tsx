"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner, SuccessBanner } from "@/components/product/Banner";
import { Button } from "@/components/product/Button";
import { CaptchaGate } from "@/components/product/CaptchaGate";
import { Checkbox } from "@/components/product/Checkbox";
import { Field, TextInput } from "@/components/product/Field";
import type { FrozenCaptcha } from "@/lib/captcha/types";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";

export default function RegisterPage() {
  const router = useRouter();
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [agree, setAgree] = useState(false);
  const [captcha, setCaptcha] = useState<FrozenCaptcha | null>(null);
  const [captchaEpoch, setCaptchaEpoch] = useState(0);
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [success, setSuccess] = useState<string | null>(null);

  async function sendCode() {
    if (!phone.trim()) {
      setError(new Error(COPY.phoneNeed));
      return;
    }
    if (!captcha) {
      setError(new Error(COPY.captchaNeed));
      return;
    }
    setSending(true);
    setError(null);
    try {
      await clientApi.sendCode({ phone, scene: "register", captchaId: captcha.captchaId, captchaData: captcha.captchaData });
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
    if (!agree) {
      setError(new Error("请先勾选用户协议"));
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await clientApi.register({ phone, password, code, agreeTerms: true });
      setSuccess(COPY.successRegister);
      router.push("/me");
      router.refresh();
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }

  return (
    <AppShell variant="task">
      <div className="kjx-auth">
        <h1 className="kjx-title">{COPY.register}</h1>
        <form className="kjx-form kjx-card" onSubmit={onSubmit}>
          <Field id="reg-phone" label={COPY.phone} hint="仅支持中国大陆手机号">
            <TextInput
              id="reg-phone"
              name="phone"
              inputMode="numeric"
              autoComplete="tel"
              required
              pattern="^1[3-9]\d{9}$"
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
            />
          </Field>
          <Field id="reg-password" label={COPY.password} hint="8～32 位，须同时包含字母和数字">
            <TextInput
              id="reg-password"
              name="password"
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              maxLength={32}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </Field>
          <CaptchaGate value={captcha} onChange={setCaptcha} disabled={sending} refreshToken={captchaEpoch} />
          <Field id="reg-code" label={COPY.smsCode}>
            <TextInput
              id="reg-code"
              name="code"
              inputMode="numeric"
              required
              pattern="^\d{6}$"
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
            disabled={!phone.trim() || !captcha}
          >
            {COPY.sendCode}
          </Button>
          <Checkbox id="reg-agree" checked={agree} onCheckedChange={setAgree}>
            {COPY.agreeTerms}
          </Checkbox>
          {error ? <ErrorBanner error={error} /> : null}
          {success ? <SuccessBanner>{success}</SuccessBanner> : null}
          <div className="kjx-actions">
            <Button type="submit" loading={loading} disabled={!agree}>
              {COPY.register}
            </Button>
            <Link className="kjx-btn kjx-btn-ghost" href="/login">
              {COPY.login}
            </Link>
          </div>
        </form>
      </div>
    </AppShell>
  );
}

"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner, SuccessBanner } from "@/components/product/Banner";
import { Button } from "@/components/product/Button";
import { CaptchaField } from "@/components/product/CaptchaField";
import { Field, TextInput } from "@/components/product/Field";
import type { FrozenCaptcha } from "@/lib/captcha/types";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";

export default function ResetPasswordPage() {
  const router = useRouter();
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
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
      await clientApi.sendCode({
        phone,
        scene: "reset_pwd",
        captchaId: captcha.captchaId,
        captchaData: captcha.captchaData,
      });
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
      await clientApi.resetPassword({ phone, code, newPassword });
      setSuccess("密码已重置，请重新登录");
      router.push("/login");
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }

  return (
    <AppShell variant="task">
      <div className="kjx-auth">
        <h1 className="kjx-title">{COPY.resetPassword}</h1>
        <form className="kjx-form kjx-card" onSubmit={onSubmit}>
          <Field id="reset-phone" label={COPY.phone}>
            <TextInput
              id="reset-phone"
              required
              inputMode="numeric"
              autoComplete="tel"
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
            />
          </Field>
          <CaptchaField value={captcha} onChange={setCaptcha} disabled={sending} refreshToken={captchaEpoch} />
          <Field id="reset-code" label={COPY.smsCode}>
            <TextInput
              id="reset-code"
              required
              inputMode="numeric"
              value={code}
              onChange={(event) => setCode(event.target.value)}
            />
          </Field>
          <Button
            type="button"
            variant="ghost"
            loading={sending}
            pendingLabel={COPY.sendingCode}
            disabled={!captcha}
            onClick={() => void sendCode()}
          >
            {COPY.sendCode}
          </Button>
          <Field id="reset-password" label={COPY.newPassword} hint="8～32 位，须同时包含字母和数字">
            <TextInput
              id="reset-password"
              type="password"
              required
              minLength={8}
              maxLength={32}
              autoComplete="new-password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </Field>
          {error ? <ErrorBanner error={error} /> : null}
          {success ? <SuccessBanner>{success}</SuccessBanner> : null}
          <Button type="submit" loading={loading}>
            {COPY.resetPassword}
          </Button>
        </form>
      </div>
    </AppShell>
  );
}

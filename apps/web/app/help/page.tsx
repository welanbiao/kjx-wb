"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner, StatusBanner, SuccessBanner } from "@/components/product/Banner";
import { Button } from "@/components/product/Button";
import { CaptchaGate } from "@/components/product/CaptchaGate";
import { Field, SelectInput, TextArea, TextInput } from "@/components/product/Field";
import { LoadingState } from "@/components/product/States";
import type { FrozenCaptcha } from "@/lib/captcha/types";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";
import { isApiError } from "@/lib/errors";
import { formatApiTime } from "@/lib/format";
import { useLoadOnMount } from "@/lib/load";
import type { AccountProfile } from "@/lib/types";

export default function HelpPage() {
  const router = useRouter();
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const { state, reload } = useLoadOnMount(async () => {
    try {
      const profile = await clientApi.profile();
      return profile.data;
    } catch (err) {
      if (isApiError(err) && err.httpStatus === 401) {
        router.replace("/login?next=/help");
      }
      throw err;
    }
  });

  return (
    <AppShell>
      <section className="kjx-section">
        <div className="kjx-wrap">
          <h1 className="kjx-title">帮助</h1>
          {state.kind === "loading" ? <LoadingState /> : null}
          {state.kind === "error" ? <ErrorBanner error={state.error} onRetry={() => void reload()} /> : null}
          {error ? <ErrorBanner error={error} /> : null}
          {notice ? <SuccessBanner>{notice}</SuccessBanner> : null}
          {state.kind === "ok" ? (
            <>
              <ContactForm
                onDone={(message) => {
                  setNotice(message);
                  setError(null);
                }}
                onError={setError}
              />
              <details className="kjx-fold">
                <summary>{COPY.cancelAccount}</summary>
                <CancelForm
                  profile={state.data}
                  onChanged={async (message) => {
                    setNotice(message);
                    setError(null);
                    await reload();
                  }}
                  onError={setError}
                />
              </details>
            </>
          ) : null}
        </div>
      </section>
    </AppShell>
  );
}

function ContactForm({
  onDone,
  onError,
}: {
  onDone: (message: string) => void;
  onError: (error: unknown) => void;
}) {
  const [appealType, setAppealType] = useState<"CHANGE_DOUYIN" | "WRONG_BIND" | "OTHER">("OTHER");
  const [content, setContent] = useState("");
  const [captcha, setCaptcha] = useState<FrozenCaptcha | null>(null);
  const [captchaEpoch, setCaptchaEpoch] = useState(0);
  const [sending, setSending] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!captcha) {
      onError(new Error(COPY.captchaNeed));
      return;
    }
    setSending(true);
    onError(undefined);
    try {
      await clientApi.appeal({ appealType, content });
      setContent("");
      setCaptcha(null);
      onDone("已收到，我们会处理这条联系");
    } catch (err) {
      onError(err);
      setCaptcha(null);
      setCaptchaEpoch((value) => value + 1);
    } finally {
      setSending(false);
    }
  }

  return (
    <article className="kjx-copy">
      <h2>联系我们</h2>
      <form className="kjx-form" onSubmit={onSubmit}>
        <Field id="help-type" label={COPY.appealType}>
          <SelectInput
            id="help-type"
            value={appealType}
            onChange={(event) => setAppealType(event.target.value as "CHANGE_DOUYIN" | "WRONG_BIND" | "OTHER")}
          >
            <option value="CHANGE_DOUYIN">更换抖音号</option>
            <option value="WRONG_BIND">绑定有误</option>
            <option value="OTHER">其他</option>
          </SelectInput>
        </Field>
        <Field id="help-content" label="想说的话">
          <TextArea id="help-content" required value={content} onChange={(event) => setContent(event.target.value)} />
        </Field>
        <CaptchaGate value={captcha} onChange={setCaptcha} disabled={sending} refreshToken={captchaEpoch} />
        <Button type="submit" loading={sending} disabled={!content.trim() || !captcha}>
          联系我们
        </Button>
      </form>
    </article>
  );
}

function CancelForm({
  profile,
  onChanged,
  onError,
}: {
  profile: AccountProfile;
  onChanged: (message: string) => Promise<void>;
  onError: (error: unknown) => void;
}) {
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [reason, setReason] = useState("");
  const [captcha, setCaptcha] = useState<FrozenCaptcha | null>(null);
  const [captchaEpoch, setCaptchaEpoch] = useState(0);
  const [sending, setSending] = useState(false);
  const [saving, setSaving] = useState(false);
  const [revoking, setRevoking] = useState(false);

  async function sendCode() {
    if (!phone.trim()) {
      onError(new Error(COPY.phoneNeed));
      return;
    }
    if (!captcha) {
      onError(new Error(COPY.captchaNeed));
      return;
    }
    setSending(true);
    onError(undefined);
    try {
      await clientApi.sendCode({
        phone,
        scene: "cancel",
        captchaId: captcha.captchaId,
        captchaData: captcha.captchaData,
      });
      await onChanged(COPY.codeSent);
    } catch (err) {
      onError(err);
    } finally {
      setSending(false);
      setCaptchaEpoch((value) => value + 1);
    }
  }

  async function cancel(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    onError(undefined);
    try {
      await clientApi.cancelAccount({ code, reason: reason || undefined });
      await onChanged("已进入注销冷静期");
    } catch (err) {
      onError(err);
    } finally {
      setSaving(false);
    }
  }

  async function revoke() {
    setRevoking(true);
    onError(undefined);
    try {
      await clientApi.revokeCancel();
      await onChanged("已撤销注销申请");
    } catch (err) {
      onError(err);
    } finally {
      setRevoking(false);
    }
  }

  if (profile.status === 3) {
    return (
      <>
        <p className="kjx-hint">{COPY.cancelHint}</p>
        <StatusBanner>注销冷静期中{profile.cancelApplyAt ? ` · ${formatApiTime(profile.cancelApplyAt)}` : ""}</StatusBanner>
        <Button variant="ghost" loading={revoking} onClick={() => void revoke()}>
          {COPY.revokeCancel}
        </Button>
      </>
    );
  }

  return (
    <form className="kjx-form" onSubmit={cancel}>
      <p className="kjx-hint">{COPY.cancelHint}</p>
      <Field id="cancel-phone" label={COPY.phone}>
        <TextInput
          id="cancel-phone"
          required
          inputMode="numeric"
          autoComplete="tel"
          value={phone}
          onChange={(event) => setPhone(event.target.value)}
        />
      </Field>
      <CaptchaGate value={captcha} onChange={setCaptcha} disabled={sending} refreshToken={captchaEpoch} />
      <Field id="cancel-code" label={COPY.smsCode}>
        <TextInput id="cancel-code" required inputMode="numeric" value={code} onChange={(event) => setCode(event.target.value)} />
      </Field>
      <Button
        type="button"
        variant="ghost"
        loading={sending}
        pendingLabel={COPY.sendingCode}
        disabled={!phone.trim() || !captcha}
        onClick={() => void sendCode()}
      >
        {COPY.sendCode}
      </Button>
      <Field id="cancel-reason" label={COPY.cancelReason}>
        <TextInput id="cancel-reason" maxLength={255} value={reason} onChange={(event) => setReason(event.target.value)} />
      </Field>
      <Button type="submit" loading={saving}>
        {COPY.cancelAccount}
      </Button>
    </form>
  );
}

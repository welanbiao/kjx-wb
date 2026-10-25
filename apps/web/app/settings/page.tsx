"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner, SuccessBanner } from "@/components/product/Banner";
import { Button } from "@/components/product/Button";
import { CaptchaGate } from "@/components/product/CaptchaGate";
import { Field, TextInput } from "@/components/product/Field";
import { LoadingState } from "@/components/product/States";
import type { FrozenCaptcha } from "@/lib/captcha/types";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";
import { isApiError } from "@/lib/errors";
import { useLoadOnMount } from "@/lib/load";
import type { AiSettingsView } from "@/lib/types";

export default function SettingsPage() {
  const router = useRouter();
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const { state, reload } = useLoadOnMount(async () => {
    try {
      const [profile, settings] = await Promise.all([clientApi.profile(), clientApi.aiSettings()]);
      return { nickname: profile.data.nickname, settings: settings.data };
    } catch (err) {
      if (isApiError(err) && err.httpStatus === 401) {
        router.replace("/login?next=/settings");
      }
      throw err;
    }
  });

  return (
    <AppShell>
      <section className="kjx-section">
        <div className="kjx-wrap">
          <h1 className="kjx-title">设置</h1>
          {state.kind === "loading" ? <LoadingState /> : null}
          {state.kind === "error" ? <ErrorBanner error={state.error} onRetry={() => void reload()} /> : null}
          {error ? <ErrorBanner error={error} /> : null}
          {notice ? <SuccessBanner>{notice}</SuccessBanner> : null}
          {state.kind === "ok" ? (
            <>
              <details className="kjx-fold">
                <summary>修改昵称</summary>
                <NicknameForm
                  nickname={state.data.nickname}
                  onDone={(message) => {
                    setNotice(message);
                    setError(null);
                    void reload();
                  }}
                  onError={setError}
                />
              </details>
              <details className="kjx-fold">
                <summary>修改密码</summary>
                <PasswordForm
                  onDone={(message) => {
                    setNotice(message);
                    setError(null);
                  }}
                  onError={setError}
                />
              </details>
              <div className="kjx-pair">
                <article className="kjx-copy">
                  <h2>模型设置</h2>
                  <ModelList settings={state.data.settings} />
                  <details className="kjx-fold">
                    <summary>更新模型接口</summary>
                    <ModelForm
                      onDone={(message) => {
                        setNotice(message);
                        setError(null);
                        void reload();
                      }}
                      onError={setError}
                    />
                  </details>
                </article>
                <article className="kjx-copy">
                  <h2>用量</h2>
                  <p>桶累积 0 token</p>
                  <p className="kjx-hint">按作品拆开的 token 占比还没有可展示的记录。平台用量账本只在平台通道结算时写入，自备密钥的游玩不进这张表。</p>
                </article>
              </div>
            </>
          ) : null}
        </div>
      </section>
    </AppShell>
  );
}

function ModelList({ settings }: { settings: AiSettingsView }) {
  if (!settings.configured) {
    return <p className="kjx-hint">尚未配置模型接口。</p>;
  }
  return (
    <ul className="kjx-form">
      <li>
        <p>{settings.model || "未命名模型"}</p>
        <p className="kjx-hint">{settings.baseUrl}</p>
        <p className="kjx-hint">状态 已配置 · 延迟未记录</p>
      </li>
    </ul>
  );
}

function ModelForm({ onDone, onError }: { onDone: (message: string) => void; onError: (error: unknown) => void }) {
  const [baseUrl, setBaseUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [model, setModel] = useState("");
  const [saving, setSaving] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    onError(undefined);
    try {
      await clientApi.saveAiSettings({ baseUrl, apiKey, model });
      setApiKey("");
      onDone(COPY.aiSettingsSaved);
    } catch (err) {
      onError(err);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="kjx-form" onSubmit={onSubmit}>
      <p className="kjx-hint">{COPY.aiSettingsHint}</p>
      <Field id="set-ai-base" label={COPY.aiBaseUrl}>
        <TextInput id="set-ai-base" required value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} />
      </Field>
      <Field id="set-ai-key" label={COPY.aiApiKey}>
        <TextInput id="set-ai-key" type="password" required value={apiKey} onChange={(event) => setApiKey(event.target.value)} />
      </Field>
      <Field id="set-ai-model" label={COPY.aiModel}>
        <TextInput id="set-ai-model" required value={model} onChange={(event) => setModel(event.target.value)} />
      </Field>
      <Button type="submit" loading={saving}>
        {COPY.aiSettingsSave}
      </Button>
    </form>
  );
}

function NicknameForm({
  nickname,
  onDone,
  onError,
}: {
  nickname: string | null;
  onDone: (message: string) => void;
  onError: (error: unknown) => void;
}) {
  const [value, setValue] = useState(nickname ?? "");
  const [saving, setSaving] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    onError(undefined);
    try {
      await clientApi.updateProfile({ nickname: value });
      onDone("昵称已更新");
    } catch (err) {
      onError(err);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="kjx-form" onSubmit={onSubmit}>
      <Field id="set-nickname" label={COPY.nickname}>
        <TextInput id="set-nickname" maxLength={64} value={value} onChange={(event) => setValue(event.target.value)} />
      </Field>
      <Button type="submit" variant="ghost" loading={saving} disabled={!value.trim()}>
        {COPY.nicknameSave}
      </Button>
    </form>
  );
}

function PasswordForm({ onDone, onError }: { onDone: (message: string) => void; onError: (error: unknown) => void }) {
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [captcha, setCaptcha] = useState<FrozenCaptcha | null>(null);
  const [captchaEpoch, setCaptchaEpoch] = useState(0);
  const [saving, setSaving] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!captcha) {
      onError(new Error(COPY.captchaNeed));
      return;
    }
    setSaving(true);
    onError(undefined);
    try {
      await clientApi.changePassword({ oldPassword, newPassword });
      setOldPassword("");
      setNewPassword("");
      setCaptcha(null);
      onDone("密码已修改");
    } catch (err) {
      onError(err);
      setCaptcha(null);
      setCaptchaEpoch((value) => value + 1);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="kjx-form" onSubmit={onSubmit}>
      <CaptchaGate value={captcha} onChange={setCaptcha} disabled={saving} refreshToken={captchaEpoch} />
      {captcha ? (
        <>
          <Field id="set-old-password" label={COPY.oldPassword}>
            <TextInput id="set-old-password" type="password" required value={oldPassword} onChange={(event) => setOldPassword(event.target.value)} />
          </Field>
          <Field id="set-new-password" label={COPY.newPassword} hint="8～32 位，须同时包含字母和数字">
            <TextInput
              id="set-new-password"
              type="password"
              required
              minLength={8}
              maxLength={32}
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </Field>
          <Button type="submit" loading={saving}>
            {COPY.changePassword}
          </Button>
        </>
      ) : (
        <p className="kjx-hint">完成图形验证后才能修改密码。</p>
      )}
    </form>
  );
}

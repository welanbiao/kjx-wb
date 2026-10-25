"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner, SuccessBanner } from "@/components/product/Banner";
import { Button } from "@/components/product/Button";
import { Field, TextInput } from "@/components/product/Field";
import { LoadingState } from "@/components/product/States";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";
import { isApiError } from "@/lib/errors";
import { useLoadOnMount } from "@/lib/load";

export default function SecurityPage() {
  const router = useRouter();
  const [success, setSuccess] = useState<string | null>(null);
  const [actionError, setActionError] = useState<unknown>();
  const { state, reload } = useLoadOnMount(async () => {
    try {
      await clientApi.profile();
      return { ok: true };
    } catch (err) {
      if (isApiError(err) && err.httpStatus === 401) {
        router.replace("/login?next=/me/security");
      }
      throw err;
    }
  });
  const data = state.kind === "ok" ? state.data : null;

  return (
    <AppShell variant="task">
      <section className="kjx-section">
        <div className="kjx-wrap">
          <h1 className="kjx-title">{COPY.security}</h1>
          {state.kind === "loading" ? <LoadingState /> : null}
          {state.kind === "error" ? <ErrorBanner error={state.error} onRetry={() => void reload()} /> : null}
          {actionError ? <ErrorBanner error={actionError} /> : null}
          {success ? <SuccessBanner>{success}</SuccessBanner> : null}
          {data ? (
            <p className="kjx-hint">
              昵称和密码改到 <Link href="/settings">设置</Link>。
            </p>
          ) : null}
        </div>
      </section>
    </AppShell>
  );
}

function AiSettingsCard({
  onDone,
  onError,
}: {
  onDone: (message: string) => Promise<void>;
  onError: (error: unknown) => void;
}) {
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
      await onDone(COPY.aiSettingsSaved);
    } catch (err) {
      onError(err);
    } finally {
      setSaving(false);
    }
  }

  return (
    <article className="kjx-card">
      <h2>{COPY.aiSettings}</h2>
      <p className="kjx-hint">{COPY.aiSettingsHint}</p>
      <form className="kjx-form" onSubmit={onSubmit}>
        <Field id="ai-base-url" label={COPY.aiBaseUrl} hint="例如兼容 OpenAI 的 /v1 地址，由你自己填写。">
          <TextInput
            id="ai-base-url"
            type="text"
            inputMode="url"
            autoComplete="off"
            required
            value={baseUrl}
            onChange={(event) => setBaseUrl(event.target.value)}
          />
        </Field>
        <Field id="ai-api-key" label={COPY.aiApiKey} hint="提交后由服务端按账号保存，不会写进日志。">
          <TextInput
            id="ai-api-key"
            type="password"
            autoComplete="off"
            required
            value={apiKey}
            onChange={(event) => setApiKey(event.target.value)}
          />
        </Field>
        <Field id="ai-model" label={COPY.aiModel}>
          <TextInput
            id="ai-model"
            autoComplete="off"
            required
            value={model}
            onChange={(event) => setModel(event.target.value)}
          />
        </Field>
        <Button type="submit" loading={saving}>
          {COPY.aiSettingsSave}
        </Button>
      </form>
    </article>
  );
}

function PasswordCard({
  onDone,
  onError,
}: {
  onDone: (message: string) => Promise<void>;
  onError: (error: unknown) => void;
}) {
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [saving, setSaving] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    onError(undefined);
    try {
      await clientApi.changePassword({ oldPassword, newPassword });
      setOldPassword("");
      setNewPassword("");
      await onDone("密码已修改");
    } catch (err) {
      onError(err);
    } finally {
      setSaving(false);
    }
  }

  return (
    <article className="kjx-card">
      <h2>{COPY.changePassword}</h2>
      <form className="kjx-form" onSubmit={onSubmit}>
        <Field id="old-password" label={COPY.oldPassword}>
          <TextInput
            id="old-password"
            type="password"
            autoComplete="current-password"
            required
            value={oldPassword}
            onChange={(event) => setOldPassword(event.target.value)}
          />
        </Field>
        <Field id="new-password" label={COPY.newPassword} hint="8～32 位，须同时包含字母和数字">
          <TextInput
            id="new-password"
            type="password"
            autoComplete="new-password"
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
      </form>
    </article>
  );
}

"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner, StatusBanner, SuccessBanner } from "@/components/product/Banner";
import { Button } from "@/components/product/Button";
import { CaptchaField } from "@/components/product/CaptchaField";
import { Field, TextInput } from "@/components/product/Field";
import { EmptyState, LoadingState } from "@/components/product/States";
import type { FrozenCaptcha } from "@/lib/captcha/types";
import { clientApi } from "@/lib/client-api";
import { COPY } from "@/lib/copy";
import { isApiError } from "@/lib/errors";
import { formatApiTime } from "@/lib/format";
import { useLoadOnMount } from "@/lib/load";
import type { AccountProfile, DeviceItem } from "@/lib/types";

type SecurityPayload = {
  profile: AccountProfile;
  devices: DeviceItem[];
};

export default function SecurityPage() {
  const router = useRouter();
  const [success, setSuccess] = useState<string | null>(null);
  const [actionError, setActionError] = useState<unknown>();
  const { state, reload } = useLoadOnMount(async () => {
    try {
      const [profile, devices] = await Promise.all([clientApi.profile(), clientApi.devices()]);
      const payload: SecurityPayload = { profile: profile.data, devices: devices.data };
      return payload;
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
          <p className="kjx-hint">
            <Link href="/me">← {COPY.profile}</Link>
          </p>
          <h1 className="kjx-title">{COPY.security}</h1>
          {state.kind === "loading" ? <LoadingState /> : null}
          {state.kind === "error" ? <ErrorBanner error={state.error} onRetry={() => void reload()} /> : null}
          {actionError ? <ErrorBanner error={actionError} /> : null}
          {success ? <SuccessBanner>{success}</SuccessBanner> : null}
          {data ? (
            <div className="kjx-grid">
              <PasswordCard
                onDone={async (message) => {
                  setSuccess(message);
                  setActionError(undefined);
                }}
                onError={setActionError}
              />
              <SessionCard
                onDone={async (message) => {
                  setSuccess(message);
                  setActionError(undefined);
                }}
                onError={setActionError}
              />
              <DevicesCard
                devices={data.devices}
                onChanged={async (message) => {
                  setSuccess(message);
                  setActionError(undefined);
                  await reload();
                }}
                onError={setActionError}
              />
              <CancelCard
                profile={data.profile}
                onChanged={async (message) => {
                  setSuccess(message);
                  setActionError(undefined);
                  await reload();
                }}
                onError={setActionError}
              />
            </div>
          ) : null}
        </div>
      </section>
    </AppShell>
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

function SessionCard({
  onDone,
  onError,
}: {
  onDone: (message: string) => Promise<void>;
  onError: (error: unknown) => void;
}) {
  const [loading, setLoading] = useState(false);

  async function refresh() {
    setLoading(true);
    onError(undefined);
    try {
      await clientApi.refresh();
      await onDone("会话已刷新");
    } catch (err) {
      onError(err);
    } finally {
      setLoading(false);
    }
  }

  return (
    <article className="kjx-card">
      <h2>{COPY.refreshSession}</h2>
      <p className="kjx-hint">令牌由 BFF 写入 HttpOnly Cookie，页面看不到 token。</p>
      <Button onClick={() => void refresh()} loading={loading}>
        {COPY.refreshSession}
      </Button>
    </article>
  );
}

function DevicesCard({
  devices,
  onChanged,
  onError,
}: {
  devices: DeviceItem[];
  onChanged: (message: string) => Promise<void>;
  onError: (error: unknown) => void;
}) {
  const [busyId, setBusyId] = useState<number | null>(null);

  async function remove(id: number) {
    setBusyId(id);
    onError(undefined);
    try {
      await clientApi.deleteDevice(id);
      await onChanged("设备已下线");
    } catch (err) {
      onError(err);
    } finally {
      setBusyId(null);
    }
  }

  return (
    <article className="kjx-card">
      <h2>{COPY.devices}</h2>
      {devices.length === 0 ? <EmptyState title="暂无设备记录" /> : null}
      <ul className="kjx-form">
        {devices.map((device) => (
          <li key={device.id} className="kjx-banner">
            <p>
              {device.deviceName || device.deviceId}
              {device.current ? ` · ${COPY.currentDevice}` : ""}
            </p>
            <p className="kjx-hint">
              {device.platform ?? "web"} · {formatApiTime(device.lastActiveAt)}
            </p>
            <Button variant="ghost" loading={busyId === device.id} onClick={() => void remove(device.id)}>
              {COPY.removeDevice}
            </Button>
          </li>
        ))}
      </ul>
    </article>
  );
}

function CancelCard({
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

  return (
    <article className="kjx-card">
      <h2>{COPY.cancelAccount}</h2>
      <p className="kjx-hint">{COPY.cancelHint}</p>
      {profile.status === 3 ? (
        <>
          <StatusBanner>注销冷静期中{profile.cancelApplyAt ? ` · ${formatApiTime(profile.cancelApplyAt)}` : ""}</StatusBanner>
          <Button variant="ghost" loading={revoking} onClick={() => void revoke()}>
            {COPY.revokeCancel}
          </Button>
        </>
      ) : (
        <form className="kjx-form" onSubmit={cancel}>
          <Field id="cancel-phone" label={COPY.phone} hint="发码接口仍要求明文手机号；资料接口只返回掩码。">
            <TextInput
              id="cancel-phone"
              required
              inputMode="numeric"
              autoComplete="tel"
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
            />
          </Field>
          <CaptchaField value={captcha} onChange={setCaptcha} disabled={sending} refreshToken={captchaEpoch} />
          <Field id="cancel-code" label={COPY.smsCode}>
            <TextInput id="cancel-code" required inputMode="numeric" value={code} onChange={(event) => setCode(event.target.value)} />
          </Field>
            <Button
            type="button"
            variant="ghost"
            loading={sending}
            pendingLabel={COPY.sendingCode}
            disabled={!captcha || !phone}
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
      )}
    </article>
  );
}

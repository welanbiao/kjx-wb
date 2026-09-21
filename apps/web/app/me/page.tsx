"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AppShell } from "@/components/product/AppShell";
import { ErrorBanner, StatusBanner, SuccessBanner } from "@/components/product/Banner";
import { Button } from "@/components/product/Button";
import { Field, SelectInput, TextArea, TextInput } from "@/components/product/Field";
import { QuotaStrip } from "@/components/product/QuotaStrip";
import { EmptyState, LoadingState } from "@/components/product/States";
import { clientApi, isMockToolsEnabled } from "@/lib/client-api";
import { COPY } from "@/lib/copy";
import { isApiError } from "@/lib/errors";
import { bindingStatusLabel, bindStatusNumberLabel, formatApiTime, memberStatusLabel } from "@/lib/format";
import { useLoadOnMount } from "@/lib/load";
import type { AccountProfile, BindingStatusData, HostOption, MemberSnapshot } from "@/lib/types";

type MePayload = {
  profile: AccountProfile;
  binding: BindingStatusData;
  member: MemberSnapshot | null;
  hosts: HostOption[];
};

export default function MePage() {
  const router = useRouter();
  const [success, setSuccess] = useState<string | null>(null);
  const [actionError, setActionError] = useState<unknown>();
  const { state, reload } = useLoadOnMount(async () => {
    try {
      const [profileRes, bindingRes, memberRes, hostsRes] = await Promise.all([
        clientApi.profile(),
        clientApi.binding(),
        clientApi.memberProfile(),
        clientApi.hosts(),
      ]);
      const payload: MePayload = {
        profile: profileRes.data,
        binding: bindingRes.data,
        member: memberRes.data,
        hosts: hostsRes.data,
      };
      return payload;
    } catch (err) {
      if (isApiError(err) && err.httpStatus === 401) {
        router.replace("/login?next=/me");
      }
      throw err;
    }
  });

  const data = state.kind === "ok" ? state.data : null;

  return (
    <AppShell variant="task">
      <section className="kjx-section">
        <div className="kjx-wrap">
          <h1 className="kjx-title">{COPY.profile}</h1>
          {state.kind === "loading" ? <LoadingState /> : null}
          {state.kind === "error" ? <ErrorBanner error={state.error} onRetry={() => void reload()} /> : null}
          {actionError ? <ErrorBanner error={actionError} /> : null}
          {success ? <SuccessBanner>{success}</SuccessBanner> : null}
          {data ? (
            <>
              <div className="kjx-grid">
                <article className="kjx-card">
                  <h2>{COPY.account}</h2>
                  <p>{data.profile.nickname || "未设置昵称"}</p>
                  <p className="kjx-hint">{data.profile.phoneMask}</p>
                  <p className="kjx-hint">绑定 {bindStatusNumberLabel(data.profile.bindStatus)}</p>
                  <p className="kjx-hint">创建时间 {formatApiTime(data.profile.createTime)}</p>
                </article>
                <article className="kjx-card">
                  <h2>{COPY.member}</h2>
                  {data.member ? (
                    <>
                      <p>
                        {COPY.memberCodeLabel} {data.member.memberCode}
                      </p>
                      <p className="kjx-hint">
                        状态 {memberStatusLabel(data.member.status)} · 抖音号 {data.member.douyinId ?? "—"}
                      </p>
                      <p className="kjx-hint">
                        {COPY.startAt} {formatApiTime(data.member.startAt)}
                      </p>
                      <p className="kjx-hint">
                        {COPY.expireAt} {formatApiTime(data.member.expireAt)}
                      </p>
                      {typeof data.member.subscribedMonths === "number" ? (
                        <p className="kjx-hint">
                          {COPY.subscribedMonths} {data.member.subscribedMonths}
                        </p>
                      ) : null}
                      {data.member.status === "EXPIRED" ? <p className="kjx-hint">{COPY.expiredHint}</p> : null}
                      {data.member.status === "NOT_STARTED" ? <p className="kjx-hint">{COPY.notStartedHint}</p> : null}
                      <QuotaStrip member={data.member} />
                    </>
                  ) : (
                    <EmptyState title="尚未同步会员信息" detail="绑定成功后由接口返回，不在前端计算。" />
                  )}
                </article>
              </div>
              <BindingPanel
                binding={data.binding}
                hosts={data.hosts}
                onChanged={async (message) => {
                  setSuccess(message);
                  setActionError(undefined);
                  await reload();
                }}
                onError={setActionError}
              />
            </>
          ) : null}
        </div>
      </section>
    </AppShell>
  );
}

function BindingPanel({
  binding,
  hosts,
  onChanged,
  onError,
}: {
  binding: BindingStatusData;
  hosts: HostOption[];
  onChanged: (message: string) => Promise<void>;
  onError: (error: unknown) => void;
}) {
  const [hostCode, setHostCode] = useState(hosts[0]?.hostCode ?? "");
  const [douyinId, setDouyinId] = useState("");
  const [memberCode, setMemberCode] = useState("");
  const [claimMonths, setClaimMonths] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [approving, setApproving] = useState(false);
  const [appealContent, setAppealContent] = useState("");
  const [appealing, setAppealing] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    onError(undefined);
    try {
      const result = await clientApi.submitBinding({
        hostCode: hostCode || undefined,
        douyinId,
        memberCode,
        claimMonths: Number(claimMonths),
      });
      await onChanged(result.data.status === "MATCHED" ? "绑定已通过" : COPY.successBinding);
    } catch (err) {
      onError(err);
    } finally {
      setSubmitting(false);
    }
  }

  async function approve() {
    setApproving(true);
    onError(undefined);
    try {
      await clientApi.mockApproveBinding();
      await onChanged("模拟审核已通过，会员信息已刷新");
    } catch (err) {
      onError(err);
    } finally {
      setApproving(false);
    }
  }

  async function submitAppeal() {
    setAppealing(true);
    onError(undefined);
    try {
      await clientApi.appeal({ appealType: "OTHER", content: appealContent });
      await onChanged("申诉已提交");
      setAppealContent("");
    } catch (err) {
      onError(err);
    } finally {
      setAppealing(false);
    }
  }

  return (
    <article className="kjx-card" style={{ marginTop: "1rem" }}>
      <h2>{COPY.binding}</h2>
      <p className="kjx-hint">
        当前状态 {bindingStatusLabel(binding.status)}
        {binding.submitAt ? ` · 提交于 ${formatApiTime(binding.submitAt)}` : ""}
      </p>
      {binding.status === "NONE" || binding.status === "REJECTED" ? (
        <>
          {binding.status === "REJECTED" ? (
            <StatusBanner>
              {COPY.rejectedPrefix}：{binding.rejectReason ?? "未提供原因"}
            </StatusBanner>
          ) : (
            <>
              <h3>{COPY.unboundTitle}</h3>
              <p className="kjx-lead">{COPY.unboundBody}</p>
            </>
          )}
          <form className="kjx-form" onSubmit={onSubmit}>
            {hosts.length > 1 ? (
              <Field id="bind-host" label={COPY.host}>
                <SelectInput id="bind-host" value={hostCode} onChange={(event) => setHostCode(event.target.value)}>
                  {hosts.map((host) => (
                    <option key={host.hostCode} value={host.hostCode}>
                      {host.hostName}（{host.hostCode}）
                    </option>
                  ))}
                </SelectInput>
              </Field>
            ) : null}
            <Field id="bind-douyin" label={COPY.douyin} hint="用户在抖音的个人主页可见">
              <TextInput id="bind-douyin" required value={douyinId} onChange={(event) => setDouyinId(event.target.value)} />
            </Field>
            <Field id="bind-months" label={COPY.months} hint="用户填「我买/订阅了几个月」，仅用于绑定核对">
              <TextInput
                id="bind-months"
                required
                inputMode="numeric"
                value={claimMonths}
                onChange={(event) => setClaimMonths(event.target.value)}
              />
            </Field>
            <Field id="bind-code" label={COPY.memberCode} hint="由主播或客服发放，半私密">
              <TextInput id="bind-code" required value={memberCode} onChange={(event) => setMemberCode(event.target.value)} />
            </Field>
            <Button type="submit" loading={submitting}>
              {COPY.submitBinding}
            </Button>
          </form>
        </>
      ) : null}
      {binding.status === "PENDING_REVIEW" ? (
        <div>
          <StatusBanner>{COPY.pending}</StatusBanner>
          {isMockToolsEnabled() ? (
            <>
              <p className="kjx-hint">{COPY.mockApproveHint}</p>
              <Button onClick={() => void approve()} loading={approving}>
                {COPY.mockApprove}
              </Button>
            </>
          ) : null}
        </div>
      ) : null}
      {binding.status === "MATCHED" ? (
        <p className="kjx-hint">已绑定。额度与状态见上方会员信息，均来自接口字段。</p>
      ) : null}
      {binding.status === "UNBINDING" ? <StatusBanner>{COPY.unbinding}</StatusBanner> : null}
      <div className="kjx-form" style={{ marginTop: "1rem" }}>
        <Field id="appeal-content" label={COPY.appeal}>
          <TextArea id="appeal-content" value={appealContent} onChange={(event) => setAppealContent(event.target.value)} />
        </Field>
        <Button variant="ghost" onClick={() => void submitAppeal()} loading={appealing} disabled={!appealContent}>
          {COPY.appeal}
        </Button>
      </div>
      <p className="kjx-hint">
        也可前往 <Link href="/unlocks">{COPY.unlocks}</Link> 查看已购作品。
      </p>
    </article>
  );
}

"use client";

import { COPY } from "@/lib/copy";

export function CaptchaField({
  checked,
  onChange,
  disabled,
}: {
  checked: boolean;
  onChange: (value: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <div className="kjx-field">
      <span>{COPY.captcha}</span>
      <label className="kjx-check">
        <input
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={(event) => onChange(event.target.checked)}
        />
        我已完成图形验证（Mock）
      </label>
      <p className="kjx-hint">{COPY.captchaHint}</p>
    </div>
  );
}

export function captchaPayload(passed: boolean): { captchaId: string; captchaData: Record<string, unknown> } {
  return {
    captchaId: passed ? "mock-captcha" : "",
    captchaData: { passed },
  };
}

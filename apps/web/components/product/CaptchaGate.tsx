"use client";

import { useState } from "react";
import { CaptchaField } from "@/components/product/CaptchaField";
import type { FrozenCaptcha } from "@/lib/captcha/types";
import { COPY } from "@/lib/copy";

type Props = {
  value: FrozenCaptcha | null;
  onChange: (value: FrozenCaptcha | null) => void;
  disabled?: boolean;
  refreshToken?: number;
};

export function CaptchaGate({ value, onChange, disabled, refreshToken = 0 }: Props) {
  const [open, setOpen] = useState(false);
  if (!open) {
    return (
      <div className="kjx-actions kjx-actions-single">
        <button
          type="button"
          className="kjx-btn kjx-btn-ghost"
          onClick={() => {
            onChange(null);
            setOpen(true);
          }}
        >
          {COPY.captchaOpen}
        </button>
      </div>
    );
  }
  return <CaptchaField value={value} onChange={onChange} disabled={disabled} refreshToken={refreshToken} />;
}

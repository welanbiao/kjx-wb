"use client";

import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "solid" | "ghost";
  loading?: boolean;
  pendingLabel?: string;
  success?: boolean;
  children: ReactNode;
};

export const Button = forwardRef<HTMLButtonElement, Props>(function Button(
  {
    variant = "solid",
    loading = false,
    pendingLabel = "提交中…",
    success = false,
    children,
    disabled,
    className = "",
    type = "button",
    ...rest
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={`kjx-btn ${variant === "ghost" ? "kjx-btn-ghost" : ""} ${className}`.trim()}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      data-success={success || undefined}
      {...rest}
    >
      {loading ? pendingLabel : children}
    </button>
  );
});

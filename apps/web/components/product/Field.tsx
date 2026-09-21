"use client";

import { cloneElement, isValidElement, type InputHTMLAttributes, type ReactElement, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";

type FieldProps = {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
};

export function Field({ id, label, hint, error, children }: FieldProps) {
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;
  const control =
    isValidElement(children) && typeof children.props === "object"
      ? cloneElement(children as ReactElement<{ id?: string; "aria-describedby"?: string }>, {
          id,
          "aria-describedby": describedBy,
        })
      : children;
  return (
    <div className="kjx-field">
      <label htmlFor={id}>{label}</label>
      {control}
      {hint ? (
        <p id={hintId} className="kjx-hint">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="kjx-field-error" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  invalid?: boolean;
};

export function TextInput({ invalid, className = "", ...rest }: InputProps) {
  return <input className={className} aria-invalid={invalid || undefined} {...rest} />;
}

export function SelectInput(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} />;
}

export function TextArea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...props} />;
}

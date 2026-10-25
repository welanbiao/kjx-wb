"use client";

import * as CheckboxPrimitive from "@radix-ui/react-checkbox";
import type { ReactNode } from "react";

type Props = {
  id: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  children: ReactNode;
};

export function Checkbox({ id, checked, onCheckedChange, children }: Props) {
  return (
    <label className="kjx-check">
      <CheckboxPrimitive.Root
        id={id}
        className="kjx-checkbox"
        checked={checked}
        onCheckedChange={(value) => onCheckedChange(value === true)}
      >
        <CheckboxPrimitive.Indicator className="kjx-checkbox-mark">✓</CheckboxPrimitive.Indicator>
      </CheckboxPrimitive.Root>
      <span>{children}</span>
    </label>
  );
}

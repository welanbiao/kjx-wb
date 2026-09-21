import {
  BIND_STATUS_NUMBER_LABEL,
  BINDING_STATUS_LABEL,
  MEMBER_STATUS_LABEL,
  WORK_STATUS_LABEL,
  WORK_TYPE_LABEL,
} from "@/lib/copy";
import type {
  BindStatus,
  BindingStatus,
  MemberStatus,
  WorkStatus,
  WorkType,
} from "@/lib/types";

/** Render API timestamps as-is. Do not infer member validity from dates. */
export function formatApiTime(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  return value.replace("T", " ").replace(/\+08:00$/, " +08:00");
}

export function bindingStatusLabel(status: BindingStatus): string {
  return BINDING_STATUS_LABEL[status];
}

export function bindStatusNumberLabel(status: BindStatus): string {
  return BIND_STATUS_NUMBER_LABEL[status];
}

export function memberStatusLabel(status: MemberStatus): string {
  return MEMBER_STATUS_LABEL[status];
}

export function workStatusLabel(status: WorkStatus): string {
  return WORK_STATUS_LABEL[status] ?? status;
}

export function workTypeLabel(type: WorkType): string {
  return WORK_TYPE_LABEL[type];
}

export function maskPhone(phone: string): string {
  if (phone.length < 7) {
    return phone;
  }
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
}

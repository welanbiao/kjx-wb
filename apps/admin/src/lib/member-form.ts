import { expandExpireDate, isExpireAt } from "./expire-at";
import type { CreateHostMemberRequest, UpdateHostMemberRequest } from "./types";

export type MemberTermInput = {
  douyinId: string;
  subscribedMonths: number | null;
  subscribedDays: number | null;
  expireDate: string;
  remark: string;
};

export type FieldKey = "douyinId" | "subscribedMonths" | "subscribedDays" | "expireDate" | "remark";

/** Change one field only. Months, days and expire date never rewrite each other. */
export function patchMemberTerm<K extends FieldKey>(
  current: MemberTermInput,
  key: K,
  value: MemberTermInput[K],
): MemberTermInput {
  return { ...current, [key]: value };
}

export function validateReason(reason: string | undefined): string {
  const trimmed = reason?.trim() ?? "";
  if (!trimmed) {
    throw new Error("必须填写操作理由");
  }
  if (trimmed.length > 500) {
    throw new Error("操作理由不能超过 500 字");
  }
  return trimmed;
}

export function validateCreateMember(input: MemberTermInput): CreateHostMemberRequest {
  const douyinId = input.douyinId.trim();
  if (!douyinId) {
    throw new Error("抖音号必填");
  }
  if (input.subscribedMonths == null) {
    throw new Error("已订阅月数必填，且不得由天数推导");
  }
  if (!Number.isInteger(input.subscribedMonths) || input.subscribedMonths < 1 || input.subscribedMonths > 120) {
    throw new Error("已订阅月数须为 1～120 的整数");
  }
  if (input.subscribedDays == null) {
    throw new Error("累计已订阅天数必填，且不得由月数推导");
  }
  if (!Number.isInteger(input.subscribedDays) || input.subscribedDays < 0 || input.subscribedDays > 36500) {
    throw new Error("累计已订阅天数须为 0～36500 的整数");
  }
  if (!input.expireDate.trim()) {
    throw new Error("到期时间必填，且不得由月数或天数推导");
  }
  const expireAt = isExpireAt(input.expireDate)
    ? input.expireDate.trim()
    : expandExpireDate(input.expireDate);
  return {
    douyinId,
    subscribedMonths: input.subscribedMonths,
    subscribedDays: input.subscribedDays,
    expireAt,
    remark: input.remark.trim(),
  };
}

export function validateUpdateMember(
  input: MemberTermInput,
  reason: string,
): UpdateHostMemberRequest {
  const validReason = validateReason(reason);
  const body: UpdateHostMemberRequest = { reason: validReason };
  if (input.subscribedMonths != null) {
    if (!Number.isInteger(input.subscribedMonths) || input.subscribedMonths < 1 || input.subscribedMonths > 120) {
      throw new Error("已订阅月数须为 1～120 的整数");
    }
    body.subscribedMonths = input.subscribedMonths;
  }
  if (input.subscribedDays != null) {
    if (!Number.isInteger(input.subscribedDays) || input.subscribedDays < 0 || input.subscribedDays > 36500) {
      throw new Error("累计已订阅天数须为 0～36500 的整数");
    }
    body.subscribedDays = input.subscribedDays;
  }
  if (input.expireDate.trim()) {
    body.expireAt = isExpireAt(input.expireDate)
      ? input.expireDate.trim()
      : expandExpireDate(input.expireDate);
  }
  body.remark = input.remark;
  return body;
}

export function emptyMemberTerm(): MemberTermInput {
  return {
    douyinId: "",
    subscribedMonths: null,
    subscribedDays: null,
    expireDate: "",
    remark: "",
  };
}

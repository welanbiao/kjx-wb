import { validateReason } from "./member-form";
import type { ApproveBindingRequest, HandleAppealRequest, ReviewReasonRequest } from "./types";

export function buildApproveBinding(remark?: string): ApproveBindingRequest {
  const trimmed = remark?.trim() ?? "";
  return trimmed ? { remark: trimmed } : {};
}

export function buildRejectBinding(reason: string): ReviewReasonRequest {
  return { reason: validateReason(reason) };
}

export function buildForceUnbind(reason: string): ReviewReasonRequest {
  return { reason: validateReason(reason) };
}

export function buildHandleAppeal(result: "APPROVED" | "REJECTED", handleNote: string): HandleAppealRequest {
  return {
    result,
    handleNote: validateReason(handleNote),
  };
}

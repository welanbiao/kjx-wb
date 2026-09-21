import { validateReason } from "../lib/member-form";
import type { AdjustEntitlementRequest, EntitlementAction } from "../lib/types";

/**
 * Isolates WP-13 entitlement writes to the frozen OpenAPI body.
 * Do not add guessed fields (quotaDelta, expireAt, entitlementId, etc.).
 */
export type FrozenAdjustEntitlementRequest = AdjustEntitlementRequest;

export function toAdjustEntitlementBody(input: {
  action: EntitlementAction;
  workCode: string;
  reason: string;
}): FrozenAdjustEntitlementRequest {
  const workCode = input.workCode.trim();
  if (!workCode) {
    throw new Error("workCode 必填");
  }
  if (input.action !== "ADD" && input.action !== "REMOVE") {
    throw new Error("action 仅支持 ADD 或 REMOVE");
  }
  return {
    action: input.action,
    workCode,
    reason: validateReason(input.reason),
  };
}

/** Reserved for WP-13 list/detail payloads. Currently unused by OpenAPI. */
export type PendingWp13EntitlementView = never;

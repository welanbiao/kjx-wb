import { describe, expect, it } from "vitest";
import { toAdjustEntitlementBody } from "../api/entitlement-adapter";
import { apiFetch } from "./api";
import { loginMock, resetMockStore } from "../mock/store";

describe("entitlement adapter", () => {
  it("only serializes frozen action/workCode/reason", () => {
    const body = toAdjustEntitlementBody({
      action: "ADD",
      workCode: "breakup-editor",
      reason: "客服补发",
    });
    expect(Object.keys(body).sort()).toEqual(["action", "reason", "workCode"]);
  });

  it("rejects empty reason", () => {
    expect(() =>
      toAdjustEntitlementBody({ action: "REMOVE", workCode: "breakup-editor", reason: "" }),
    ).toThrow(/理由/);
  });

  it("mock adjust posts official_add unlock record", async () => {
    resetMockStore();
    const operator = loginMock("operator", "mock-operator");
    const payload = await apiFetch<{ unlockedCount: number }>(
      "/admin-api/kjx/member/1002/entitlement",
      {
        method: "POST",
        token: operator.token,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action: "ADD", workCode: "breakup-editor", reason: "补偿" }),
      },
    );
    expect(payload.data.unlockedCount).toBeGreaterThan(0);
  });
});

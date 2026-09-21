import { describe, expect, it } from "vitest";
import { buildApproveBinding, buildForceUnbind, buildHandleAppeal, buildRejectBinding } from "./review-actions";

describe("review action payloads", () => {
  it("approve sends optional remark only", () => {
    expect(buildApproveBinding()).toEqual({});
    expect(buildApproveBinding("ok")).toEqual({ remark: "ok" });
  });

  it("reject and force-unbind require reason", () => {
    expect(() => buildRejectBinding("")).toThrow(/理由/);
    expect(buildRejectBinding("信息不符")).toEqual({ reason: "信息不符" });
    expect(buildForceUnbind("客服确认")).toEqual({ reason: "客服确认" });
  });

  it("appeal handle requires result and handleNote", () => {
    expect(buildHandleAppeal("APPROVED", "已核实")).toEqual({
      result: "APPROVED",
      handleNote: "已核实",
    });
    expect(() => buildHandleAppeal("REJECTED", "")).toThrow(/理由/);
  });
});

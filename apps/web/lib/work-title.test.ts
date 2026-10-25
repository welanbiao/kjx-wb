import { describe, expect, it } from "vitest";
import { decodeWorkName, decodeWorkSummary } from "./work-title";

describe("work page decoder", () => {
  it("replaces live-row question marks with the Chinese title", () => {
    expect(decodeWorkName("breakup-editor", "??????")).toBe("分手结局修改器");
    expect(decodeWorkName("breakup-editor", "????????")).toBe("分手结局修改器");
    expect(decodeWorkSummary("breakup-editor", "????")).toContain("那句话");
  });

  it("keeps an intact Chinese name", () => {
    expect(decodeWorkName("breakup-editor", "分手结局修改器")).toBe("分手结局修改器");
    expect(decodeWorkName("midnight-script", "午夜剧本")).toBe("午夜剧本");
  });
});

import { describe, expect, it } from "vitest";
import { FORM_A_TYPES, isFormAType } from "@/runtime/form-a/components";

describe("Form A whitelist", () => {
  it("covers the fixed component set and rejects unknown types", () => {
    expect(FORM_A_TYPES).toHaveLength(44);
    expect(isFormAType("chat.bubble")).toBe(true);
    expect(isFormAType("custom.slot:wallet")).toBe(true);
    expect(isFormAType("script")).toBe(false);
    expect(isFormAType("custom.slot:bad/name")).toBe(false);
  });
});

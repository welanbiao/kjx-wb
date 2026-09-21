import { describe, expect, it } from "vitest";
import { normalizePublicBase } from "./public-base";

describe("normalizePublicBase", () => {
  it("keeps direct-dev root base", () => {
    expect(normalizePublicBase(undefined)).toBe("/");
    expect(normalizePublicBase("")).toBe("/");
    expect(normalizePublicBase("/")).toBe("/");
    expect(normalizePublicBase(" / ")).toBe("/");
  });

  it("normalizes the Nginx /admin/ mount", () => {
    expect(normalizePublicBase("/admin")).toBe("/admin/");
    expect(normalizePublicBase("/admin/")).toBe("/admin/");
    expect(normalizePublicBase("admin")).toBe("/admin/");
  });
});

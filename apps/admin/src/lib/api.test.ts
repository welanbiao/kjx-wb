import { describe, expect, it } from "vitest";
import { ADMIN_API_PREFIX, buildHeaders } from "./api";

describe("admin api client", () => {
  it("uses /admin-api instead of retired /api", () => {
    expect(ADMIN_API_PREFIX).toBe("/admin-api");
  });

  it("does not inject a forged Authorization header", () => {
    const headers = buildHeaders();
    expect(headers.has("Authorization")).toBe(false);
    expect(headers.get("X-Request-Id")).toBeTruthy();
  });
});

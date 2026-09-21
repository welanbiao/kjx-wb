import { describe, expect, it } from "vitest";
import { APP_API_PREFIX, buildHeaders } from "./api";
import { rememberCredential, TOKEN_STORAGE } from "./session";

describe("web api client", () => {
  it("uses the frozen /app prefix instead of retired /api", () => {
    expect(APP_API_PREFIX).toBe("/app");
  });

  it("does not inject Authorization from browser storage", () => {
    const headers = buildHeaders();
    expect(headers.has("Authorization")).toBe(false);
    expect(headers.get("X-Client-Version")).toBeTruthy();
    expect(headers.get("X-Request-Id")).toBeTruthy();
  });
});

describe("web session policy", () => {
  it("declares httpOnly cookie storage", () => {
    expect(TOKEN_STORAGE).toBe("httpOnly-cookie");
  });

  it("refuses to persist credentials", () => {
    expect(() => rememberCredential("secret")).toThrow(/httpOnly cookies/);
  });
});

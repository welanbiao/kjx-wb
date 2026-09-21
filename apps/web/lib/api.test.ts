import { describe, expect, it } from "vitest";
import { APP_API_PREFIX, buildHeaders } from "./api";
import { rememberCredential, TOKEN_STORAGE, cookieHasTokenName, SESSION_COOKIE } from "./session";
import { takeAuthToken } from "./bff/strip-token";
import { memberStatusLabel } from "./format";
import { displayErrorMessage } from "./errors";

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

  it("detects the session cookie name without exposing it as JS storage", () => {
    expect(cookieHasTokenName(`${SESSION_COOKIE}=abc`)).toBe(true);
    expect(cookieHasTokenName("kjx_csrf=abc")).toBe(false);
  });
});

describe("BFF token stripping", () => {
  it("removes token from login-shaped envelopes", () => {
    const result = takeAuthToken({
      code: 0,
      msg: "",
      requestId: "r1",
      data: { token: "satoken-secret", expireIn: 10, account: { id: 1 } },
    });
    expect(result.token).toBe("satoken-secret");
    expect((result.envelope.data as { token?: string }).token).toBeUndefined();
  });
});

describe("display mapping", () => {
  it("only maps API member status and never infers from dates", () => {
    expect(memberStatusLabel("EXPIRED")).toBe("已过期");
    expect(memberStatusLabel("VALID")).toBe("有效");
    expect(memberStatusLabel("NOT_STARTED")).toBe("尚未生效");
  });

  it("shows error code catalog text with request-friendly fallback", () => {
    expect(displayErrorMessage(1006, "")).toMatch(/过期/);
    expect(displayErrorMessage(1005, "", { time: "2026-10-01T00:00:00.000+08:00" })).toMatch(/2026-10-01/);
  });
});

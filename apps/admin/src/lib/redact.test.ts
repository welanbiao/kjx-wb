import { describe, expect, it } from "vitest";
import { redactRecord } from "./redact";

describe("redact", () => {
  it("never keeps phone, token or captcha in a log payload", () => {
    const redacted = redactRecord({
      phone: "13800138000",
      token: "secret",
      captcha: "1234",
      authorization: "Bearer abc",
      memberCode: "KJX-A3F9K2B7",
    });
    expect(redacted.phone).toBe("[redacted]");
    expect(redacted.token).toBe("[redacted]");
    expect(redacted.captcha).toBe("[redacted]");
    expect(redacted.authorization).toBe("[redacted]");
    expect(redacted.memberCode).toBe("KJX-A3F9K2B7");
  });
});

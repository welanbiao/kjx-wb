import { describe, expect, it } from "vitest";
import {
  IFRAME_SANDBOX,
  acceptSandboxMessage,
  hostReply,
  payloadLooksSafeForIframe,
} from "@/runtime/sandbox/guard";

describe("sandbox postMessage guard", () => {
  const nonce = "n1";
  const allowedOrigin = "https://demo.game.example";

  it("accepts nonce origin and method whitelist", () => {
    const accepted = acceptSandboxMessage({
      data: { type: "kjx-rpc", nonce, method: "state.save", id: "1", payload: { slot: "auto" } },
      origin: allowedOrigin,
      expectedNonce: nonce,
      allowedOrigin,
    });
    expect(accepted.ok).toBe(true);
  });

  it("rejects wrong origin nonce and methods", () => {
    expect(
      acceptSandboxMessage({
        data: { type: "kjx-rpc", nonce, method: "state.save", id: "1" },
        origin: "https://evil.example",
        expectedNonce: nonce,
        allowedOrigin,
      }).ok,
    ).toBe(false);
    expect(
      acceptSandboxMessage({
        data: { type: "kjx-rpc", nonce: "other", method: "state.save", id: "1" },
        origin: allowedOrigin,
        expectedNonce: nonce,
        allowedOrigin,
      }).ok,
    ).toBe(false);
    expect(
      acceptSandboxMessage({
        data: { type: "kjx-rpc", nonce, method: "eval", id: "1" },
        origin: allowedOrigin,
        expectedNonce: nonce,
        allowedOrigin,
      }).ok,
    ).toBe(false);
  });

  it("never treats token cookie payloads as safe iframe replies", () => {
    expect(payloadLooksSafeForIframe({ token: "abc" })).toBe(false);
    expect(payloadLooksSafeForIframe({ Authorization: "Bearer x" })).toBe(false);
    expect(payloadLooksSafeForIframe({ cookie: "a=b" })).toBe(false);
    expect(payloadLooksSafeForIframe({ checksum: "ok" })).toBe(true);
    expect(IFRAME_SANDBOX.includes("allow-same-origin")).toBe(false);
    const reply = hostReply("1", nonce, true, { checksum: "ok" });
    expect(JSON.stringify(reply)).not.toMatch(/token|cookie/i);
  });
});

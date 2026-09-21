import { describe, expect, it } from "vitest";
import { ADMIN_API_PREFIX, apiFetch, buildHeaders, newIdempotencyKey } from "./api";
import { loginMock, resetMockStore } from "../mock/store";

describe("admin api client", () => {
  it("uses /admin-api instead of retired /api", () => {
    expect(ADMIN_API_PREFIX).toBe("/admin-api");
  });

  it("does not inject a forged Authorization header", () => {
    const headers = buildHeaders(undefined, { token: null });
    expect(headers.has("Authorization")).toBe(false);
    expect(headers.get("X-Request-Id")).toBeTruthy();
  });

  it("attaches Authorization only when a session token exists", () => {
    const headers = buildHeaders(undefined, { token: "mock-session" });
    expect(headers.get("Authorization")).toBe("Bearer mock-session");
  });

  it("sends idempotency key on demand", () => {
    const key = newIdempotencyKey();
    expect(key.length).toBeGreaterThanOrEqual(8);
    const headers = buildHeaders(undefined, { idempotencyKey: key, token: null });
    expect(headers.get("X-Idempotency-Key")).toBe(key);
  });

  it("rejects non-zero ApiBody with requestId", async () => {
    resetMockStore();
    await expect(
      apiFetch("/admin-api/kjx/self/host", { token: "missing" }),
    ).rejects.toMatchObject({ code: 401, requestId: expect.any(String) });
  });

  it("login mock returns HOST session without real credentials", async () => {
    resetMockStore();
    const session = loginMock("host-a", "mock-host-a");
    const payload = await apiFetch("/admin-api/kjx/self/host", { token: session.token });
    expect(payload.code).toBe(0);
    expect(payload.data).toMatchObject({ hostCode: "KJX", deptId: 101 });
  });
});

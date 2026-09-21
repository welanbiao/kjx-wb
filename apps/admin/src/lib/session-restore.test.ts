import { describe, expect, it } from "vitest";
import { apiFetch } from "./api";
import { loginMock, resetMockStore, sessionFromToken } from "../mock/store";

describe("mock session restore", () => {
  it("rebuilds a persona after in-memory store reset", async () => {
    resetMockStore();
    const hostA = loginMock("host-a", "mock-host-a");
    const token = hostA.token;
    resetMockStore();
    const restored = sessionFromToken(token);
    expect(restored?.username).toBe("host-a");
    const page = await apiFetch<{ list: Array<{ deptId: number }> }>(
      "/admin-api/kjx/self/member/page",
      { token },
    );
    expect(page.data.list.every((item) => item.deptId === 101)).toBe(true);
  });
});

import { describe, expect, it } from "vitest";
import { apiFetch } from "./api";
import { loginMock, resetMockStore } from "../mock/store";

describe("HOST A cannot see HOST B data", () => {
  it("self member page only returns the logged-in department", async () => {
    resetMockStore();
    const hostA = loginMock("host-a", "mock-host-a");
    const page = await apiFetch<{ list: Array<{ deptId: number; memberCode: string }> }>(
      "/admin-api/kjx/self/member/page?pageNo=1&pageSize=20",
      { token: hostA.token },
    );
    expect(page.data.list.length).toBeGreaterThan(0);
    expect(page.data.list.every((item) => item.deptId === 101)).toBe(true);
    expect(page.data.list.some((item) => item.memberCode.startsWith("XYZ-"))).toBe(false);
  });

  it("HOST A cannot read HOST B member code via self API", async () => {
    resetMockStore();
    const hostA = loginMock("host-a", "mock-host-a");
    await expect(
      apiFetch("/admin-api/kjx/self/member/1002/code", { token: hostA.token }),
    ).rejects.toMatchObject({ httpStatus: 404 });
  });

  it("HOST A is forbidden from OPERATOR member APIs", async () => {
    resetMockStore();
    const hostA = loginMock("host-a", "mock-host-a");
    await expect(
      apiFetch("/admin-api/kjx/member/page", { token: hostA.token }),
    ).rejects.toMatchObject({ httpStatus: 403 });
    await expect(
      apiFetch("/admin-api/kjx/binding/2003/approve", {
        method: "POST",
        token: hostA.token,
        body: "{}",
      }),
    ).rejects.toMatchObject({ httpStatus: 403 });
  });

  it("OPERATOR can query both departments and cannot call HOST self host", async () => {
    resetMockStore();
    const operator = loginMock("operator", "mock-operator");
    const page = await apiFetch<{ list: Array<{ deptId: number }> }>(
      "/admin-api/kjx/member/page?pageNo=1&pageSize=20",
      { token: operator.token },
    );
    const depts = new Set(page.data.list.map((item) => item.deptId));
    expect(depts.has(101)).toBe(true);
    expect(depts.has(102)).toBe(true);
    await expect(
      apiFetch("/admin-api/kjx/self/host", { token: operator.token }),
    ).rejects.toMatchObject({ httpStatus: 403 });
  });
});

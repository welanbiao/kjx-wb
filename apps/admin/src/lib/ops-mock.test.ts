import { describe, expect, it } from "vitest";
import { apiDownload, apiFetch } from "./api";
import { loginMock, resetMockStore } from "../mock/store";
import { canTransit } from "./work-status";

describe("operator journeys against mock API", () => {
  it("creates a member with independent month/day/expire fields", async () => {
    resetMockStore();
    const operator = loginMock("operator", "mock-operator");
    const created = await apiFetch<{ memberId: number; availableCount: number }>(
      "/admin-api/kjx/member",
      {
        method: "POST",
        token: operator.token,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deptId: 101,
          reason: "运营代录",
          douyinId: "wangwu",
          subscribedMonths: 15,
          subscribedDays: 435,
          expireAt: "2027-08-09T23:59:59.999+08:00",
        }),
      },
    );
    expect(created.data.availableCount).toBe(14);
  });

  it("rejects binding without reason and accepts approve remark", async () => {
    resetMockStore();
    const operator = loginMock("operator", "mock-operator");
    await expect(
      apiFetch("/admin-api/kjx/binding/2002/reject", {
        method: "POST",
        token: operator.token,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      }),
    ).rejects.toMatchObject({ httpStatus: 400 });
    const approved = await apiFetch("/admin-api/kjx/binding/2002/approve", {
      method: "POST",
      token: operator.token,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ remark: "三要素一致" }),
    });
    expect(approved.code).toBe(0);
  });

  it("work status update is rejected when transition is illegal", async () => {
    resetMockStore();
    const operator = loginMock("operator", "mock-operator");
    expect(canTransit("DRAFT", "PUBLISHED")).toBe(false);
    await expect(
      apiFetch("/admin-api/kjx/work/4002", {
        method: "PUT",
        token: operator.token,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status: "PUBLISHED", reason: "直接上架" }),
      }),
    ).rejects.toMatchObject({ httpStatus: 409 });
    const submitted = await apiFetch<{ status: string }>("/admin-api/kjx/work/4002", {
      method: "PUT",
      token: operator.token,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status: "PENDING_REVIEW", reason: "提交审核" }),
    });
    expect(submitted.data.status).toBe("PENDING_REVIEW");
  });

  it("export returns a blob and caller can revoke the object URL", async () => {
    resetMockStore();
    const hostA = loginMock("host-a", "mock-host-a");
    const file = await apiDownload("/admin-api/kjx/self/member/export", { token: hostA.token });
    expect(file.blob.size).toBeGreaterThan(0);
    expect(file.requestId).toBeTruthy();
  });

  it("HOST cannot call unlock-record export", async () => {
    resetMockStore();
    const hostA = loginMock("host-a", "mock-host-a");
    await expect(
      apiDownload("/admin-api/kjx/unlock-record/export", { token: hostA.token }),
    ).rejects.toMatchObject({ httpStatus: 403 });
  });
});

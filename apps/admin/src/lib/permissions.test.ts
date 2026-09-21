import { describe, expect, it } from "vitest";
import {
  accessForPath,
  canAccess,
  canPress,
  HOST_SELF_PATHS,
  OPS_PATHS,
  PERMISSION_MATRIX,
  visibleMenu,
  type AccessClass,
} from "./permissions";
import type { AdminSession, RoleKey } from "./types";

function session(roles: RoleKey[], deptId = 101): AdminSession {
  return { token: "t", username: "u", userId: 1, deptId, roles };
}

describe("permission matrix", () => {
  it("HOST cannot open OPERATOR review or cross-dept pages", () => {
    const host = session(["HOST"]);
    for (const path of OPS_PATHS) {
      expect(canAccess(host, accessForPath(path))).toBe(false);
    }
    expect(canPress(host, "binding.approve")).toBe(false);
    expect(canPress(host, "member.recalc")).toBe(false);
    expect(visibleMenu(host).some((item) => item.path.startsWith("/ops/"))).toBe(false);
  });

  it("OPERATOR cannot use HOST self-service paths", () => {
    const operator = session(["OPERATOR"]);
    for (const path of HOST_SELF_PATHS) {
      expect(canAccess(operator, accessForPath(path))).toBe(false);
    }
    expect(visibleMenu(operator).some((item) => item.path.startsWith("/self/"))).toBe(false);
    expect(canAccess(operator, "ops")).toBe(true);
  });

  it("superadmin can manage ops pages", () => {
    const admin = session(["superadmin"]);
    expect(canAccess(admin, "ops")).toBe(true);
    expect(canAccess(admin, "host-self")).toBe(false);
    expect(canPress(admin, "work.write")).toBe(true);
  });

  it("direct URL access class is denied by canAccess even if the user types it", () => {
    const host = session(["HOST"]);
    const access: AccessClass = accessForPath("/ops/bindings");
    expect(access).toBe("ops");
    expect(canAccess(host, access)).toBe(false);
  });

  it("documents the role matrix used by the UI", () => {
    expect(PERMISSION_MATRIX["ops.bindings"].HOST).toBe(false);
    expect(PERMISSION_MATRIX["self.members"].OPERATOR).toBe(false);
    expect(PERMISSION_MATRIX["ops.works"].superadmin).toBe(true);
  });
});

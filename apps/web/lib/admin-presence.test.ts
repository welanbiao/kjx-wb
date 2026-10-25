import { describe, expect, it } from "vitest";
import { hasAdminDeskSession } from "./admin-presence";

describe("admin desk tab presence", () => {
  it("stays hidden without an admin session", () => {
    expect(hasAdminDeskSession(null)).toBe(false);
    expect(hasAdminDeskSession("")).toBe(false);
    expect(hasAdminDeskSession("not-json")).toBe(false);
    expect(hasAdminDeskSession(JSON.stringify({ username: "operator" }))).toBe(false);
  });

  it("shows the tab only when username and roles are present", () => {
    const raw = JSON.stringify({
      username: "operator",
      roles: ["OPERATOR"],
      token: "do-not-read",
    });
    expect(hasAdminDeskSession(raw)).toBe(true);
  });
});

import type { AdminSession, RoleKey } from "./types";

export type AccessClass = "public" | "auth" | "host-self" | "ops" | "unlock";

export const HOST_SELF_PATHS = [
  "/self/host",
  "/self/members",
  "/self/bindings",
] as const;

export const OPS_PATHS = [
  "/ops/hosts",
  "/ops/members",
  "/ops/bindings",
  "/ops/appeals",
  "/ops/works",
  "/ops/entitlements",
] as const;

export function isOpsRole(roles: RoleKey[]): boolean {
  return roles.includes("OPERATOR") || roles.includes("superadmin");
}

export function isHostRole(roles: RoleKey[]): boolean {
  return roles.includes("HOST") && !isOpsRole(roles);
}

export function isOps(session: AdminSession | null): boolean {
  return Boolean(session && isOpsRole(session.roles));
}

export function isHost(session: AdminSession | null): boolean {
  return Boolean(session && isHostRole(session.roles));
}

export function canAccess(session: AdminSession | null, access: AccessClass): boolean {
  if (access === "public") {
    return true;
  }
  if (!session) {
    return false;
  }
  if (access === "auth") {
    return true;
  }
  if (access === "host-self") {
    return isHost(session);
  }
  if (access === "ops") {
    return isOps(session);
  }
  if (access === "unlock") {
    return isHost(session) || isOps(session);
  }
  return false;
}

export function accessForPath(path: string): AccessClass {
  if (path === "/login") {
    return "public";
  }
  if (path.startsWith("/self/")) {
    return path.startsWith("/self/unlock") ? "unlock" : "host-self";
  }
  if (path.startsWith("/ops/unlock")) {
    return "unlock";
  }
  if (path.startsWith("/ops/")) {
    return "ops";
  }
  return "auth";
}

export type MenuItem = {
  path: string;
  title: string;
  access: AccessClass;
};

export const MENU_ITEMS: MenuItem[] = [
  { path: "/dashboard", title: "工作台", access: "auth" },
  { path: "/self/host", title: "我的主播资料", access: "host-self" },
  { path: "/self/members", title: "我的会员", access: "host-self" },
  { path: "/self/bindings", title: "绑定状态", access: "host-self" },
  { path: "/self/unlock-records", title: "解锁记录", access: "unlock" },
  { path: "/ops/hosts", title: "主播管理", access: "ops" },
  { path: "/ops/members", title: "跨部门会员", access: "ops" },
  { path: "/ops/bindings", title: "绑定审核", access: "ops" },
  { path: "/ops/appeals", title: "申诉处理", access: "ops" },
  { path: "/ops/works", title: "作品管理", access: "ops" },
  { path: "/ops/entitlements", title: "权益调整", access: "ops" },
  { path: "/ops/unlock-records", title: "解锁记录", access: "unlock" },
  { path: "/health", title: "健康检查", access: "auth" },
];

export function visibleMenu(session: AdminSession | null): MenuItem[] {
  return MENU_ITEMS.filter((item) => {
    if (!canAccess(session, item.access)) {
      return false;
    }
    if (item.path === "/self/unlock-records") {
      return isHost(session);
    }
    if (item.path === "/ops/unlock-records") {
      return isOps(session);
    }
    return true;
  });
}

export type ButtonKey =
  | "member.create"
  | "member.edit"
  | "member.export"
  | "member.code"
  | "member.recalc"
  | "member.entitlement"
  | "binding.approve"
  | "binding.reject"
  | "binding.forceUnbind"
  | "appeal.handle"
  | "host.create"
  | "host.edit"
  | "work.write";

const OPS_BUTTONS = new Set<ButtonKey>([
  "member.create",
  "member.edit",
  "member.recalc",
  "member.entitlement",
  "binding.approve",
  "binding.reject",
  "binding.forceUnbind",
  "appeal.handle",
  "host.create",
  "host.edit",
  "work.write",
]);

const HOST_BUTTONS = new Set<ButtonKey>([
  "member.create",
  "member.edit",
  "member.export",
  "member.code",
]);

export function canPress(session: AdminSession | null, key: ButtonKey): boolean {
  if (!session) {
    return false;
  }
  if (isOps(session)) {
    return OPS_BUTTONS.has(key);
  }
  if (isHost(session)) {
    return HOST_BUTTONS.has(key);
  }
  return false;
}

/** UI matrix for docs/tests. Backend remains the authorization boundary. */
export const PERMISSION_MATRIX: Record<string, { HOST: boolean; OPERATOR: boolean; superadmin: boolean }> = {
  "self.host": { HOST: true, OPERATOR: false, superadmin: false },
  "self.members": { HOST: true, OPERATOR: false, superadmin: false },
  "self.bindings": { HOST: true, OPERATOR: false, superadmin: false },
  "self.unlock": { HOST: true, OPERATOR: false, superadmin: false },
  "ops.hosts": { HOST: false, OPERATOR: true, superadmin: true },
  "ops.members": { HOST: false, OPERATOR: true, superadmin: true },
  "ops.bindings": { HOST: false, OPERATOR: true, superadmin: true },
  "ops.appeals": { HOST: false, OPERATOR: true, superadmin: true },
  "ops.works": { HOST: false, OPERATOR: true, superadmin: true },
  "ops.entitlements": { HOST: false, OPERATOR: true, superadmin: true },
  "ops.unlock": { HOST: false, OPERATOR: true, superadmin: true },
  "btn.binding.approve": { HOST: false, OPERATOR: true, superadmin: true },
  "btn.member.recalc": { HOST: false, OPERATOR: true, superadmin: true },
};

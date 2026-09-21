/**
 * 管理端静态资源与 History 前缀。
 *
 * - 直连开发（localhost:3001）：`/`
 * - 经 Nginx 挂载：`/admin/`
 */
export function normalizePublicBase(raw: string | undefined): string {
  const trimmed = raw?.trim() ?? "";
  if (trimmed === "" || trimmed === "/") {
    return "/";
  }
  const withLead = trimmed.startsWith("/") ? trimmed : `/${trimmed}`;
  return withLead.endsWith("/") ? withLead : `${withLead}/`;
}

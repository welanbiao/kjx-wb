/**
 * 会员前台会话策略占位。
 *
 * Token 只能由后端 / BFF 写入 httpOnly Cookie。
 * 本模块拒绝把凭证持久化到 Web Storage，供 WP-20 在此扩展 CSRF。
 */

export const TOKEN_STORAGE = "httpOnly-cookie" as const;

export function rememberCredential(value: string): never {
  throw new Error(
    `Refusing to persist credentials in Web Storage. Use httpOnly cookies. length=${value.length}`,
  );
}

/**
 * Token 只能由 BFF 写入 httpOnly Cookie。
 * 浏览器 JS 不得持有或持久化 Sa-Token。
 */

export const TOKEN_STORAGE = "httpOnly-cookie" as const;

export const SESSION_COOKIE = "kjx_app_token";
export const CSRF_COOKIE = "kjx_csrf";
export const DEVICE_COOKIE = "kjx_device_id";
export const CSRF_HEADER = "X-CSRF-Token";

const TOKEN_LIKE = /(token|satoken|authorization|bearer|refresh)/i;

export function rememberCredential(value: string): never {
  throw new Error(
    `Refusing to persist credentials in Web Storage. Use httpOnly cookies. length=${value.length}`,
  );
}

export function assertNoTokenInWebStorage(): void {
  if (typeof window === "undefined") {
    return;
  }
  for (const storage of [window.localStorage, window.sessionStorage]) {
    for (let index = 0; index < storage.length; index += 1) {
      const key = storage.key(index) ?? "";
      const value = storage.getItem(key) ?? "";
      if (TOKEN_LIKE.test(key) || TOKEN_LIKE.test(value)) {
        throw new Error(`Refusing token-like Web Storage entry: ${key}`);
      }
    }
  }
}

export function cookieHasTokenName(cookieSource: string): boolean {
  return cookieSource.split(";").some((part) => {
    const name = part.trim().split("=")[0] ?? "";
    return name === SESSION_COOKIE;
  });
}

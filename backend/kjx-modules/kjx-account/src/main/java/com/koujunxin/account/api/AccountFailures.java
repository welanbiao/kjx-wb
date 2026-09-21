package com.koujunxin.account.api;

/**
 * 冻结错误文案与码的工厂方法。数字与文案来自 {@code contracts/errors.yaml}，禁止另起错误码。
 */
public final class AccountFailures {

    private AccountFailures() {
    }

    /** errors.yaml VERIFY_CODE_INVALID = 1011 */
    public static AccountException verifyCodeInvalid() {
        return new AccountException(1011, 400, "验证码错误或已过期");
    }

    /** errors.yaml RATE_LIMITED = 1012 */
    public static AccountException rateLimited() {
        return new AccountException(1012, 429, "发送过于频繁，请稍后再试");
    }

    /** errors.yaml ACCOUNT_LOCKED = 1013 */
    public static AccountException accountLocked() {
        return new AccountException(1013, 403, "账号已锁定，请联系客服");
    }

    /** errors.yaml ACCOUNT_CANCELLING = 1014 */
    public static AccountException accountCancelling() {
        return new AccountException(1014, 403, "账号注销中，暂时无法操作");
    }

    /** errors.yaml IDEMPOTENCY_CONFLICT = 1090 */
    public static AccountException idempotencyConflict() {
        return new AccountException(1090, 409, "重复请求与首次参数不一致");
    }

    public static AccountException badRequest(String message) {
        return new AccountException(400, 400, message);
    }

    public static AccountException unauthorized() {
        return new AccountException(401, 401, "认证失败，无法访问系统资源");
    }

    public static AccountException notFound(String message) {
        return new AccountException(404, 404, message);
    }

    /**
     * 手机号已注册。冻结表无对应 10xx 码，仅使用 HTTP 409。
     */
    public static AccountException phoneTaken() {
        return new AccountException(409, 409, "该手机号已注册");
    }

    public static AccountException captchaInvalid() {
        return new AccountException(400, 400, "图形验证码错误或已过期");
    }
}

package com.koujunxin.member.api;

/**
 * 冻结错误文案与码的工厂。数字与文案来自 {@code contracts/errors.yaml}，禁止另起错误码。
 */
public final class MemberFailures {

    private MemberFailures() {
    }

    public static MemberException mismatch() {
        return new MemberException(1001, 400, "信息不匹配，请核对后重试");
    }

    public static MemberException taken() {
        return new MemberException(1002, 409, "该会员信息已被使用，如有疑问请联系客服");
    }

    public static MemberException alreadyBound() {
        return new MemberException(1003, 409, "每个账号只能绑定一个会员");
    }

    public static MemberException locked() {
        return new MemberException(1004, 429, "尝试次数过多，请 30 分钟后再试");
    }

    public static MemberException cancelling() {
        return new MemberException(1014, 403, "账号注销中，暂时无法操作");
    }

    public static MemberException idempotencyConflict() {
        return new MemberException(1090, 409, "重复请求与首次参数不一致");
    }

    public static MemberException badRequest(String message) {
        return new MemberException(400, 400, message);
    }

    public static MemberException unauthorized() {
        return new MemberException(401, 401, "认证失败，无法访问系统资源");
    }

    public static MemberException forbidden() {
        return new MemberException(403, 403, "没有访问权限，请联系管理员授权");
    }

    public static MemberException notFound() {
        return new MemberException(404, 404, "记录不存在或无权访问");
    }

    public static MemberException conflict(String message) {
        return new MemberException(409, 409, message);
    }
}

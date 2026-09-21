package com.koujunxin.account.api.port;

/**
 * 短信验证码投递端口。只负责把已生成的 6 位码交给提供方，禁止写日志。
 * <p>验证码本身不走 Outbox（信封禁止 verify_code / 明文手机号）。安全通知走 {@code OutboxWriter}。</p>
 */
@FunctionalInterface
public interface SmsDispatchPort {

    void dispatch(String scene, String phone, String code);
}

package com.koujunxin.server.auth;

import org.dromara.sms4j.api.entity.SmsResponse;

/**
 * SMS4J 发送入口。生产走 {@code SmsFactory}；测试注入工厂替身，禁止真发外部短信。
 */
@FunctionalInterface
public interface SmsBlendClient {

    SmsResponse sendMessage(String configId, String phone, String code);
}

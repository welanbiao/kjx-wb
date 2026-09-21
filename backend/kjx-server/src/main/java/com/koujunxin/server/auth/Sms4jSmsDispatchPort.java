package com.koujunxin.server.auth;

import org.dromara.sms4j.api.entity.SmsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.koujunxin.account.api.AccountException;
import com.koujunxin.account.api.port.SmsDispatchPort;

/**
 * SMS4J 投递适配。凭据不在本类读取；失败必须打断发送流程，禁止吞错。
 */
public final class Sms4jSmsDispatchPort implements SmsDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(Sms4jSmsDispatchPort.class);

    private final SmsBlendClient client;
    private final String configId;
    private final String provider;

    public Sms4jSmsDispatchPort(SmsBlendClient client, String configId, String provider) {
        this.client = client;
        this.configId = configId;
        this.provider = provider;
    }

    @Override
    public void dispatch(String scene, String phone, String code) {
        if (client == null || configId == null || configId.isBlank()) {
            throw new AccountException(500, 500, "短信服务未配置");
        }
        SmsResponse response;
        try {
            response = client.sendMessage(configId, phone, code);
        } catch (RuntimeException ex) {
            log.warn("sms provider call failed scene={} provider={}", scene, provider);
            throw new AccountException(500, 500, "短信发送失败");
        }
        if (!successful(response)) {
            log.warn("sms provider rejected scene={} provider={}", scene, provider);
            throw new AccountException(500, 500, "短信发送失败");
        }
    }

    private static boolean successful(SmsResponse response) {
        return response != null && response.isSuccess();
    }
}

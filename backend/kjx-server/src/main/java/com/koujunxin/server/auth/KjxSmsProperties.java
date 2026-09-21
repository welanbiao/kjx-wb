package com.koujunxin.server.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kjx.sms")
public class KjxSmsProperties {

    /**
     * fail-closed | stub | live。未显式配置时 fail-closed，禁止静默假装已投递。
     */
    private String mode = "fail-closed";
    private String provider = "";
    private String configId = "";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }
}

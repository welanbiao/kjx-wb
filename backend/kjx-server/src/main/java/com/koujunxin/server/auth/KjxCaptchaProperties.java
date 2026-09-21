package com.koujunxin.server.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kjx.captcha")
public class KjxCaptchaProperties {

    private boolean enabled = true;
    private String prefix = "captcha";
    private long expireMs = 120_000L;
    private String type = "SLIDER";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public long getExpireMs() {
        return expireMs;
    }

    public void setExpireMs(long expireMs) {
        this.expireMs = expireMs;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}

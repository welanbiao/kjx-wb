package com.koujunxin.server.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kjx.ai.platform")
public class PlatformAiProperties {

    private String baseUrl = "";
    private String apiKey = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean configured() {
        return !baseUrl.isBlank() && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "PlatformAiProperties[baseUrl=" + baseUrl + ", apiKey=redacted]";
    }
}

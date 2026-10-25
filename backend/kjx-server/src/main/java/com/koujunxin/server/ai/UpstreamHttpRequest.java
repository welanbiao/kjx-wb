package com.koujunxin.server.ai;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public record UpstreamHttpRequest(
        String method,
        String url,
        Map<String, String> headers,
        String jsonBody,
        boolean stream) {

    public UpstreamHttpRequest {
        headers = headers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(headers));
        method = method == null || method.isBlank() ? "POST" : method;
        jsonBody = jsonBody == null ? "" : jsonBody;
    }

    @Override
    public String toString() {
        return "UpstreamHttpRequest[method=" + method + ", url=" + url + ", stream=" + stream + "]";
    }
}

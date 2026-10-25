package com.koujunxin.server.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class JdkUpstreamHttpTransport implements UpstreamHttpTransport {

    private final HttpClient http;
    private final Duration timeout;

    JdkUpstreamHttpTransport(HttpClient http, Duration timeout) {
        this.http = http;
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    @Override
    public UpstreamHttpResponse send(UpstreamHttpRequest request) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url()))
                .timeout(timeout)
                .method(
                        request.method(),
                        HttpRequest.BodyPublishers.ofString(request.jsonBody(), StandardCharsets.UTF_8));
        request.headers().forEach(builder::header);
        HttpResponse<java.io.InputStream> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        String location = response.headers().firstValue("Location").orElse(null);
        return new UpstreamHttpResponse(response.statusCode(), location, response.body());
    }
}

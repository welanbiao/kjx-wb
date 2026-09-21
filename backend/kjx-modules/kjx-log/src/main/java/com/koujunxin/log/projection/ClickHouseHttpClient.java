package com.koujunxin.log.projection;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * ClickHouse HTTP 接口。无官方 JDBC 依赖，避免根 POM 散落版本；失败只抛给投影消费者。
 */
public final class ClickHouseHttpClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authHeader;
    private final Duration timeout;

    public ClickHouseHttpClient(String baseUrl, String username, String password, Duration timeout) {
        this.baseUrl = trimSlash(baseUrl);
        this.timeout = timeout == null ? Duration.ofSeconds(2) : timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        String user = username == null || username.isBlank() ? "default" : username;
        String pass = password == null ? "" : password;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }

    public void execute(String sql) {
        post(sql, "text/plain; charset=UTF-8");
    }

    public String query(String sql) {
        return post(sql, "text/plain; charset=UTF-8");
    }

    public void insertJsonEachRow(String table, String jsonLine) {
        execute("INSERT INTO " + table + " FORMAT JSONEachRow\n" + jsonLine);
    }

    public void applyClasspathSchema(String resource) {
        try (InputStream in = ClickHouseHttpClient.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource: " + resource);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                String withoutComments = stripLineComments(trimmed);
                if (!withoutComments.isBlank()) {
                    execute(withoutComments);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to apply ClickHouse schema", ex);
        }
    }

    private String post(String body, String contentType) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("ClickHouse url is not configured");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/"))
                .timeout(timeout)
                .header("Authorization", authHeader)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("ClickHouse HTTP " + response.statusCode() + ": " + trimBody(response.body()));
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ClickHouse request interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("ClickHouse unreachable: " + ex.getMessage(), ex);
        }
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return null;
        }
        String value = url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String trimBody(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    private static String stripLineComments(String sql) {
        StringBuilder builder = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) {
                continue;
            }
            builder.append(line).append('\n');
        }
        return builder.toString().trim();
    }
}

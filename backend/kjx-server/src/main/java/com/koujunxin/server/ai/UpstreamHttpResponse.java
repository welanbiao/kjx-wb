package com.koujunxin.server.ai;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class UpstreamHttpResponse implements Closeable {

    private final int status;
    private final String location;
    private final InputStream body;

    public UpstreamHttpResponse(int status, String location, InputStream body) {
        this.status = status;
        this.location = location;
        this.body = body == null ? InputStream.nullInputStream() : body;
    }

    public static UpstreamHttpResponse text(int status, String location, String body) {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        return new UpstreamHttpResponse(status, location, new ByteArrayInputStream(bytes));
    }

    public int status() {
        return status;
    }

    public String location() {
        return location;
    }

    public InputStream body() {
        return body;
    }

    public boolean redirect() {
        return status >= 300 && status < 400 && location != null && !location.isBlank();
    }

    @Override
    public void close() throws IOException {
        body.close();
    }
}

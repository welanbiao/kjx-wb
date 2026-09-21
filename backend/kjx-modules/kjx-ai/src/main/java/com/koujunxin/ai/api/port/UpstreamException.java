package com.koujunxin.ai.api.port;

public final class UpstreamException extends RuntimeException {

    public enum Kind {
        UNSUPPORTED_TOOLS,
        UNSUPPORTED_SCHEMA,
        TIMEOUT,
        CANCELLED,
        UPSTREAM
    }

    private final Kind kind;

    public UpstreamException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static UpstreamException unsupportedTools() {
        return new UpstreamException(Kind.UNSUPPORTED_TOOLS, "upstream does not support tools");
    }

    public static UpstreamException unsupportedSchema() {
        return new UpstreamException(Kind.UNSUPPORTED_SCHEMA, "upstream does not support json_schema");
    }

    public static UpstreamException timeout() {
        return new UpstreamException(Kind.TIMEOUT, "upstream idle timeout");
    }

    public static UpstreamException cancelled() {
        return new UpstreamException(Kind.CANCELLED, "upstream cancelled");
    }
}

package com.koujunxin.channel.api;

public record ResolvedChannel(ChannelMode mode, ByokCredential credential, String source) {

    public ResolvedChannel {
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (mode == ChannelMode.BYOK && credential == null) {
            throw new IllegalArgumentException("BYOK credential is required");
        }
        if (mode == ChannelMode.PLATFORM) {
            credential = null;
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
    }
}

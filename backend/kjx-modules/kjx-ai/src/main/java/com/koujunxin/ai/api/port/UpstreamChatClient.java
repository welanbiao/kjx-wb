package com.koujunxin.ai.api.port;

public interface UpstreamChatClient {

    UpstreamChatResponse complete(UpstreamChatRequest request, CancellationToken cancel);

    void stream(UpstreamChatRequest request, UpstreamStreamListener listener, CancellationToken cancel);
}

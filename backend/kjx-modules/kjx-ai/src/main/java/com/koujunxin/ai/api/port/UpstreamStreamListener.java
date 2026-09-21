package com.koujunxin.ai.api.port;

public interface UpstreamStreamListener {

    void onDelta(String textDelta);

    void onComplete(UpstreamChatResponse response);
}

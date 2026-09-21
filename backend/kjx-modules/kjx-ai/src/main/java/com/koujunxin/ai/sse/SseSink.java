package com.koujunxin.ai.sse;

public interface SseSink {

    void comment(String comment);

    void data(String payload);

    void complete();
}

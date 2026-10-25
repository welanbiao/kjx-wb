package com.koujunxin.server.ai;

import java.io.IOException;

public interface UpstreamHttpTransport {

    UpstreamHttpResponse send(UpstreamHttpRequest request) throws IOException, InterruptedException;
}

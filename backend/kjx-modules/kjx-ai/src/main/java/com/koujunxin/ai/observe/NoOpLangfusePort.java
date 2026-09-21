package com.koujunxin.ai.observe;

import com.koujunxin.ai.api.port.LangfusePort;

public final class NoOpLangfusePort implements LangfusePort {

    @Override
    public void report(AiTrace trace) {
    }
}

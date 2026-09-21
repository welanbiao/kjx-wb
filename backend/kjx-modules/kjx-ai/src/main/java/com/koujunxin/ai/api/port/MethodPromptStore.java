package com.koujunxin.ai.api.port;

import com.koujunxin.ai.prompt.PromptRecord;

/**
 * 方法论母版只允许写入与内部保管，不得组装进运行时消息，不得下发。
 */
public interface MethodPromptStore {

    void saveMethod(PromptRecord record);

    void activateMethod(String workCode, String version);
}

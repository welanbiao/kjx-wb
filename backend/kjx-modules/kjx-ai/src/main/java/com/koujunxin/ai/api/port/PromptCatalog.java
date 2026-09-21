package com.koujunxin.ai.api.port;

import java.util.Optional;

import com.koujunxin.ai.prompt.PromptKind;
import com.koujunxin.ai.prompt.PromptRecord;

/**
 * 只暴露 WORK Prompt。METHOD 母版不得经此接口读出给运行时或客户端。
 */
public interface PromptCatalog {

    PromptRecord saveWork(PromptRecord record);

    void activateWork(String workCode, String version);

    Optional<PromptRecord> findActiveWork(String workCode);

    Optional<PromptRecord> findWork(String workCode, String version);

    default PromptKind exposedKind() {
        return PromptKind.WORK;
    }
}

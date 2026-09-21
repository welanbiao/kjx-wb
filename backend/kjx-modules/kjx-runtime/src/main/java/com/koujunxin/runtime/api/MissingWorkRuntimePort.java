package com.koujunxin.runtime.api;

import java.util.List;

/**
 * 未接线时的占位。真实内容由装配方接到 WP-31，本模块不改 kjx-work。
 */
public final class MissingWorkRuntimePort implements WorkRuntimePort {

    @Override
    public WorkRuntimeManifest require(String workCode) {
        throw RuntimeFailure.notFound("WORK_PORT_UNAVAILABLE", "WorkRuntimePort not wired");
    }

    @Override
    public StageContent loadStage(String workCode, String packageVersion, String stage, List<String> reached) {
        return StageContent.gated(stage);
    }

    @Override
    public SignedAsset signAsset(String workCode, String packageVersion, String assetKey, String stage) {
        throw RuntimeFailure.notFound("WORK_PORT_UNAVAILABLE", "WorkRuntimePort not wired");
    }

    @Override
    public String sandboxOrigin(String workCode) {
        return "null";
    }
}

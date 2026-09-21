package com.koujunxin.runtime.api;

import java.util.List;

/**
 * 作品运行时目录与分段内容。装配方接到 WP-31 已有能力，禁止本模块改 kjx-work。
 */
public interface WorkRuntimePort {

    WorkRuntimeManifest require(String workCode);

    StageContent loadStage(String workCode, String packageVersion, String stage, List<String> reached);

    SignedAsset signAsset(String workCode, String packageVersion, String assetKey, String stage);

    String sandboxOrigin(String workCode);
}

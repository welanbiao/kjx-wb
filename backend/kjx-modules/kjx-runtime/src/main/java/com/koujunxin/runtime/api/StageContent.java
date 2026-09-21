package com.koujunxin.runtime.api;

import java.util.List;
import java.util.Map;

/**
 * 按阶段下发的布局/内容/素材键。无 entitlement 时不得填充真实节点。
 */
public record StageContent(
        String stage,
        String layoutJson,
        String tokensJson,
        String nodesJson,
        List<String> assetKeys,
        Map<String, String> signedAssetUrls,
        boolean gated
) {

    public StageContent {
        stage = stage == null || stage.isBlank() ? "1" : stage;
        layoutJson = blankToEmptyObject(layoutJson);
        tokensJson = blankToEmptyObject(tokensJson);
        nodesJson = blankToEmptyArray(nodesJson);
        assetKeys = assetKeys == null ? List.of() : List.copyOf(assetKeys);
        signedAssetUrls = signedAssetUrls == null ? Map.of() : Map.copyOf(signedAssetUrls);
    }

    public static StageContent gated(String stage) {
        return new StageContent(stage, "{}", "{}", "[]", List.of(), Map.of(), true);
    }

    private static String blankToEmptyObject(String json) {
        return json == null || json.isBlank() ? "{}" : json;
    }

    private static String blankToEmptyArray(String json) {
        return json == null || json.isBlank() ? "[]" : json;
    }
}

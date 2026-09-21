package com.koujunxin.runtime.disclose;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.api.StageContent;
import com.koujunxin.runtime.api.WorkRuntimePort;
import com.koujunxin.runtime.forma.FormAComponents;
import com.koujunxin.runtime.ledger.CanonicalJson;

/**
 * 按 stage 过滤布局与节点。未到达阶段与无 entitlement 不填真实内容。
 */
public final class StageDisclosure {

    private final WorkRuntimePort works;

    public StageDisclosure(WorkRuntimePort works) {
        this.works = works;
    }

    public StageContent disclose(
            boolean entitled,
            String workCode,
            String packageVersion,
            String stage,
            List<String> reached) {
        if (!entitled) {
            return StageContent.gated(stage);
        }
        StageContent raw = works.loadStage(workCode, packageVersion, stage, reached);
        FormAComponents.validateLayout(raw.layoutJson());
        ObjectNode layout = filterLayout(raw.layoutJson(), stage, reached);
        ArrayNode nodes = filterNodes(raw.nodesJson(), stage, reached);
        return new StageContent(
                stage,
                CanonicalJson.write(layout),
                raw.tokensJson(),
                CanonicalJson.write(nodes),
                raw.assetKeys(),
                raw.signedAssetUrls(),
                false);
    }

    static ObjectNode filterLayout(String layoutJson, String stage, List<String> reached) {
        JsonNode root = CanonicalJson.parse(layoutJson);
        ObjectNode copy = root.isObject() ? (ObjectNode) root.deepCopy() : CanonicalJson.MAPPER.createObjectNode();
        JsonNode screens = copy.get("screens");
        if (screens == null || !screens.isArray()) {
            return copy;
        }
        ArrayNode kept = CanonicalJson.MAPPER.createArrayNode();
        for (JsonNode screen : screens) {
            if (visible(screen, stage, reached)) {
                kept.add(screen);
            }
        }
        copy.set("screens", kept);
        return copy;
    }

    static ArrayNode filterNodes(String nodesJson, String stage, List<String> reached) {
        JsonNode root = CanonicalJson.parse(nodesJson);
        ArrayNode kept = CanonicalJson.MAPPER.createArrayNode();
        if (!root.isArray()) {
            return kept;
        }
        for (JsonNode node : root) {
            if (visible(node, stage, reached)) {
                kept.add(node);
            }
        }
        return kept;
    }

    static boolean visible(JsonNode node, String stage, List<String> reached) {
        if (!node.has("stage") || node.get("stage").isNull()) {
            return true;
        }
        String required = node.get("stage").isNumber()
                ? Integer.toString(node.get("stage").asInt())
                : node.get("stage").asText();
        if (required.isBlank()) {
            return true;
        }
        if (required.equals(stage)) {
            return true;
        }
        return reached != null && reached.contains(required);
    }

    public static List<String> reachedFrom(String payloadJson, String currentStage) {
        List<String> reached = new ArrayList<>();
        reached.add(currentStage == null || currentStage.isBlank() ? "1" : currentStage);
        JsonNode payload = CanonicalJson.parse(payloadJson);
        JsonNode array = payload.get("reached");
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                String value = item.asText();
                if (!value.isBlank() && !reached.contains(value)) {
                    reached.add(value);
                }
            }
        }
        return List.copyOf(reached);
    }

    public static boolean looksLikeServerPath(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("/server/") || lower.startsWith("server/");
    }
}

package com.koujunxin.runtime.breakup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.ledger.CanonicalJson;

public final class BreakupSessionFactory {

    private final BreakupCatalog catalog;

    public BreakupSessionFactory(BreakupCatalog catalog) {
        this.catalog = catalog;
    }

    public String initialPayload(boolean trial) {
        ObjectNode couple = catalog.coupleCopy();
        ObjectNode live = CanonicalJson.MAPPER.createObjectNode();
        live.set("nodes", couple.get("nodes").deepCopy());
        live.set("moments", couple.get("moments").deepCopy());
        live.set("notes", couple.get("notes").deepCopy());
        live.set("bills", couple.get("bills").deepCopy());
        live.set("photos", couple.get("photos").deepCopy());
        ObjectNode payload = CanonicalJson.MAPPER.createObjectNode();
        payload.put("trial", trial);
        payload.put("work", BreakupWorks.CODE);
        payload.put("coupleId", BreakupWorks.COUPLE_ID);
        payload.put("coupleLabel", BreakupWorks.COUPLE_LABEL);
        payload.put("screen", "screen-title");
        payload.put("side", "");
        payload.put("worldline", "original");
        payload.put("bp", couple.path("metrics0").path("bp").asInt(100));
        payload.put("lm", couple.path("metrics0").path("lm").asInt(24));
        payload.put("lf", couple.path("metrics0").path("lf").asInt(70));
        payload.put("timeDays", 0);
        payload.put("monitorCount", 0);
        payload.put("monitorCaught", 0);
        payload.put("msgBlocked", false);
        payload.put("ended", false);
        payload.put("wallet", 0);
        payload.put("redpacketCount", 0);
        payload.put("achievements", CanonicalJson.MAPPER.createArrayNode());
        payload.set("flags", CanonicalJson.MAPPER.createArrayNode());
        payload.set("edits", CanonicalJson.MAPPER.createArrayNode());
        payload.set("messages", CanonicalJson.MAPPER.createArrayNode());
        payload.set("persona", CanonicalJson.MAPPER.createObjectNode());
        payload.set("prompt", CanonicalJson.MAPPER.createObjectNode());
        payload.set("world", emptyWorld());
        payload.set("rewritten", live);
        payload.set("titles", freezeTitles(couple.path("nodes")));
        payload.set("reached", CanonicalJson.MAPPER.createArrayNode().add("1"));
        payload.set("stack", CanonicalJson.MAPPER.createArrayNode().add("title"));
        return CanonicalJson.write(payload);
    }

    public static ObjectNode emptyWorld() {
        ObjectNode world = CanonicalJson.MAPPER.createObjectNode();
        world.set("threadPatches", CanonicalJson.MAPPER.createObjectNode());
        world.set("noteHints", CanonicalJson.MAPPER.createArrayNode());
        return world;
    }

    private static ArrayNode freezeTitles(JsonNode nodes) {
        ArrayNode titles = CanonicalJson.MAPPER.createArrayNode();
        if (nodes == null || !nodes.isArray()) {
            return titles;
        }
        for (JsonNode node : nodes) {
            ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
            row.put("id", node.path("id").asText());
            row.put("title", node.path("title").asText());
            row.set("bridgeNarr", node.path("bridgeNarr").deepCopy());
            titles.add(row);
        }
        return titles;
    }
}

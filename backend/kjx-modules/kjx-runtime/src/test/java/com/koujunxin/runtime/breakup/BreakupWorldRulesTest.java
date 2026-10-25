package com.koujunxin.runtime.breakup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.ledger.CanonicalJson;

class BreakupWorldRulesTest {

    @Test
    void softChangesPreviewHarshDoesTooNeutralDoesNot() {
        ObjectNode session = CanonicalJson.MAPPER.createObjectNode();
        session.put("side", "a");
        session.set("world", BreakupSessionFactory.emptyWorld());
        assertTrue(BreakupWorldRules.apply(session, "对不起，别走"));
        assertEquals("你们……和好了？", session.path("world").path("threadPatches").path("juan").path("preview").asText());

        ObjectNode harsh = CanonicalJson.MAPPER.createObjectNode();
        harsh.put("side", "b");
        harsh.set("world", BreakupSessionFactory.emptyWorld());
        assertTrue(BreakupWorldRules.apply(harsh, "滚，无所谓"));
        assertEquals("对。就该这样。别回头。", harsh.path("world").path("threadPatches").path("juan").path("preview").asText());

        ObjectNode none = CanonicalJson.MAPPER.createObjectNode();
        none.put("side", "a");
        none.set("world", BreakupSessionFactory.emptyWorld());
        assertFalse(BreakupWorldRules.apply(none, "联考出分了吗"));
        assertTrue(none.path("world").path("threadPatches").isEmpty()
                || none.path("world").path("threadPatches").path("juan").isMissingNode());
    }
}

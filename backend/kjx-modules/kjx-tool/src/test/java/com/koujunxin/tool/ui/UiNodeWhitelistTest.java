package com.koujunxin.tool.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.support.ToolHarness;

class UiNodeWhitelistTest {

    private final ToolHarness harness = new ToolHarness();
    private final UiNodeWhitelist ui = harness.platformUi();
    private final ObjectMapper mapper = harness.mapper;

    @Test
    void unknownTypeIsRejected() throws Exception {
        var node = mapper.readTree("{\"type\":\"evil-widget\",\"props\":{\"text\":\"x\"}}");
        ToolFailure failure = assertThrows(ToolFailure.class, () -> ui.validateNode(node));
        assertEquals("UI_NODE_NOT_ALLOWED", failure.code());
    }

    @Test
    void rawHtmlFieldIsRejected() throws Exception {
        var node = mapper.readTree("{\"type\":\"toast\",\"props\":{\"text\":\"hi\",\"html\":\"<script>\"}}");
        ToolFailure failure = assertThrows(ToolFailure.class, () -> ui.validateNode(node));
        assertEquals("UI_PROPS_INVALID", failure.code());
    }

    @Test
    void customSlotMustMatchPattern() throws Exception {
        ui.validateNode(mapper.readTree("{\"type\":\"custom.slot:skin\",\"props\":{\"className\":\"x\"}}"));
        ToolFailure failure = assertThrows(ToolFailure.class,
                () -> ui.validateNode(mapper.readTree("{\"type\":\"custom.slot:Bad Slot\",\"props\":{}}")));
        assertEquals("UI_NODE_NOT_ALLOWED", failure.code());
    }

    @Test
    void propsMustMatchSchema() throws Exception {
        ToolFailure failure = assertThrows(ToolFailure.class,
                () -> ui.validateNode(mapper.readTree("{\"type\":\"toast\",\"props\":{}}")));
        assertEquals("UI_PROPS_INVALID", failure.code());
    }

    @Test
    void chatBubbleAllowsOptionalSpeaker() throws Exception {
        ui.validateNode(mapper.readTree(
                "{\"type\":\"chat.bubble\",\"props\":{\"text\":\"hi\",\"role\":\"npc\",\"speaker\":\"A\"}}"));
        ui.validateNode(mapper.readTree("{\"type\":\"chat.bubble\",\"props\":{\"text\":\"hi\",\"role\":\"npc\"}}"));
        ToolFailure failure = assertThrows(ToolFailure.class, () -> ui.validateNode(mapper.readTree(
                "{\"type\":\"chat.bubble\",\"props\":{\"text\":\"hi\",\"role\":\"npc\",\"channel\":\"group\"}}")));
        assertEquals("UI_PROPS_INVALID", failure.code());
    }
}

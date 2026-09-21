package com.koujunxin.tool.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class ToolJsonSchemaTest {

    private final ToolJsonSchema schema = new ToolJsonSchema();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requiredAndTypeAreEnforced() throws Exception {
        var spec = mapper.readTree("""
                {"type":"object","properties":{"delta":{"type":"number","maximum":15}},"required":["delta"],"additionalProperties":false}
                """);
        ObjectNode ok = mapper.createObjectNode();
        ok.put("delta", 3);
        assertTrue(schema.validate(spec, ok).isEmpty());

        ObjectNode missing = mapper.createObjectNode();
        assertFalse(schema.validate(spec, missing).isEmpty());

        ObjectNode extra = mapper.createObjectNode();
        extra.put("delta", 1);
        extra.put("hack", true);
        assertFalse(schema.validate(spec, extra).isEmpty());

        ObjectNode tooBig = mapper.createObjectNode();
        tooBig.put("delta", 16);
        assertFalse(schema.validate(spec, tooBig).isEmpty());
    }

    @Test
    void enumAndIntegerAreEnforced() throws Exception {
        var spec = mapper.readTree("""
                {"type":"object","properties":{"visibility":{"type":"string","enum":["public","private"]},"n":{"type":"integer"}},"required":["visibility"]}
                """);
        ObjectNode ok = mapper.createObjectNode();
        ok.put("visibility", "public");
        ok.put("n", 1);
        assertTrue(schema.validate(spec, ok).isEmpty());

        ObjectNode badEnum = mapper.createObjectNode();
        badEnum.put("visibility", "everyone");
        assertFalse(schema.validate(spec, badEnum).isEmpty());

        ObjectNode notInt = mapper.createObjectNode();
        notInt.put("visibility", "public");
        notInt.put("n", 1.5);
        assertFalse(schema.validate(spec, notInt).isEmpty());
    }
}

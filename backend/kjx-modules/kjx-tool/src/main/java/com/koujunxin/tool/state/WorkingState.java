package com.koujunxin.tool.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;
import com.koujunxin.runtime.api.dto.StateMutation;
import com.koujunxin.tool.json.JsonSupport;

/**
 * 组合/内置工具的内存工作副本。步骤间共享，结束时一次写出。
 */
public final class WorkingState {

    private final ObjectMapper mapper;
    private final ObjectNode payload;
    private final ArrayNode ui;
    private final String schemaVersion;
    private String hint = "";
    private boolean dirty;
    private boolean returned;
    private JsonNode returnValue;

    public WorkingState(ObjectMapper mapper, SessionStateSnapshot snapshot) {
        this.mapper = mapper;
        this.schemaVersion = snapshot.schemaVersion();
        JsonNode parsed = JsonSupport.read(mapper, snapshot.payloadJson());
        this.payload = parsed.isObject() ? (ObjectNode) parsed.deepCopy() : mapper.createObjectNode();
        this.ui = mapper.createArrayNode();
    }

    public ObjectNode payload() {
        return payload;
    }

    public ArrayNode ui() {
        return ui;
    }

    public boolean returned() {
        return returned;
    }

    public boolean dirty() {
        return dirty;
    }

    public String hint() {
        return hint;
    }

    public void hint(String value) {
        this.hint = value == null ? "" : value;
    }

    public void markReturned(JsonNode value) {
        this.returned = true;
        this.returnValue = value;
    }

    public void touch() {
        dirty = true;
    }

    public void patch(JsonNode delta) {
        if (delta != null && delta.isObject()) {
            JsonSupport.merge(payload, delta);
            dirty = true;
        }
    }

    public void set(String path, JsonNode value) {
        JsonSupport.set(mapper, payload, path, value);
        dirty = true;
    }

    public JsonNode get(String path) {
        return JsonSupport.get(payload, path);
    }

    public ObjectNode store() {
        JsonNode existing = payload.get("_store");
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = mapper.createObjectNode();
        payload.set("_store", created);
        dirty = true;
        return created;
    }

    public void addUi(String target, String op, JsonNode node, JsonNode value) {
        ObjectNode item = mapper.createObjectNode();
        item.put("target", target);
        item.put("op", op);
        if (node != null && !node.isNull() && !node.isMissingNode()) {
            item.set("node", node);
        }
        if (value != null && !value.isNull() && !value.isMissingNode()) {
            item.set("value", value);
        }
        ui.add(item);
    }

    public String toResultJson() {
        ObjectNode out = mapper.createObjectNode();
        out.put("ok", true);
        out.set("ui", ui);
        out.set("state", payload);
        out.put("hint", hint);
        if (returnValue != null && !returnValue.isMissingNode()) {
            out.set("value", returnValue);
        }
        return JsonSupport.write(mapper, out);
    }

    public StateMutation mutation() {
        return new StateMutation("TOOL_PATCH", schemaVersion, JsonSupport.write(mapper, payload));
    }
}

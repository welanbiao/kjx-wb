package com.koujunxin.tool.builtin;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.koujunxin.tool.state.WorkingState;

@FunctionalInterface
public interface BuiltinHandler {

    void execute(JsonNode args, WorkingState state);

    default BuiltinHandler requireNonNull() {
        return Objects.requireNonNull(this);
    }
}

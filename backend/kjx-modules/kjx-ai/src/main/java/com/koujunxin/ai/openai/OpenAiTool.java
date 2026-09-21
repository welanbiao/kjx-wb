package com.koujunxin.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiTool(String type, OpenAiFunction function) {

    public OpenAiTool {
        if (type == null || type.isBlank()) {
            type = "function";
        }
        if (function == null) {
            throw new IllegalArgumentException("function is required");
        }
    }

    public record OpenAiFunction(String name, String description, Object parameters) {
        public OpenAiFunction {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("tool name is required");
            }
        }
    }
}

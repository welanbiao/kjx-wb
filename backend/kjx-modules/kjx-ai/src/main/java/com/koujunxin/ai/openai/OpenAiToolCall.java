package com.koujunxin.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiToolCall(
        String id,
        String type,
        OpenAiFunctionCall function
) {

    public OpenAiToolCall {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("tool call id is required");
        }
        if (type == null || type.isBlank()) {
            type = "function";
        }
        if (function == null) {
            throw new IllegalArgumentException("function is required");
        }
    }

    public record OpenAiFunctionCall(String name, String arguments) {
        public OpenAiFunctionCall {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("function name is required");
            }
            if (arguments == null || arguments.isBlank()) {
                arguments = "{}";
            }
        }
    }
}

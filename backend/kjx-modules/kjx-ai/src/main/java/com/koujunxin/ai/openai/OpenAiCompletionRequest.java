package com.koujunxin.ai.openai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiCompletionRequest(
        String model,
        List<OpenAiMessage> messages,
        Boolean stream,
        List<OpenAiTool> tools,
        @JsonProperty("tool_choice") Object toolChoice,
        @JsonProperty("response_format") Object responseFormat,
        @JsonProperty("max_tokens") Integer maxTokens
) {

    public OpenAiCompletionRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}

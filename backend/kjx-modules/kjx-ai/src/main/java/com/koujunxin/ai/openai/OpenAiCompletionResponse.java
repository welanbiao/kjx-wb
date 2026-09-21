package com.koujunxin.ai.openai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiCompletionResponse(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices,
        OpenAiUsage usage
) {

    public OpenAiCompletionResponse {
        if (object == null || object.isBlank()) {
            object = "chat.completion";
        }
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    public record Choice(
            int index,
            OpenAiMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }
}

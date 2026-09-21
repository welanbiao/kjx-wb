package com.koujunxin.ai.openai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChunk(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices
) {

    public OpenAiChunk {
        if (object == null || object.isBlank()) {
            object = "chat.completion.chunk";
        }
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    public record Choice(
            int index,
            Delta delta,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    public record Delta(String role, String content) {
    }

    public static OpenAiChunk delta(String id, String model, long created, String content) {
        return new OpenAiChunk(
                id,
                "chat.completion.chunk",
                created,
                model,
                List.of(new Choice(0, new Delta(null, content), null)));
    }

    public static OpenAiChunk stop(String id, String model, long created) {
        return new OpenAiChunk(
                id,
                "chat.completion.chunk",
                created,
                model,
                List.of(new Choice(0, new Delta(null, null), "stop")));
    }
}

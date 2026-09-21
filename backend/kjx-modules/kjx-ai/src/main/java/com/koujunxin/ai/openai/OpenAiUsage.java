package com.koujunxin.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiUsage(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens,
        @JsonProperty("total_tokens") int totalTokens,
        @JsonProperty("prompt_tokens_details") PromptTokensDetails promptTokensDetails
) {

    public OpenAiUsage {
        if (totalTokens <= 0) {
            totalTokens = promptTokens + completionTokens;
        }
    }

    public static OpenAiUsage of(int prompt, int completion, int cached) {
        PromptTokensDetails details = cached > 0 ? new PromptTokensDetails(cached) : null;
        return new OpenAiUsage(prompt, completion, prompt + completion, details);
    }

    public record PromptTokensDetails(@JsonProperty("cached_tokens") int cachedTokens) {
    }
}

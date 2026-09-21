package com.koujunxin.ai.prompt;

import java.util.ArrayList;
import java.util.List;

import com.koujunxin.ai.openai.OpenAiMessage;

public record AssembledPrompt(
        String promptVersion,
        String stablePrefix,
        String variableSuffix,
        List<OpenAiMessage> history
) {

    public AssembledPrompt {
        if (promptVersion == null || promptVersion.isBlank()) {
            throw new IllegalArgumentException("promptVersion is required");
        }
        if (stablePrefix == null) {
            stablePrefix = "";
        }
        if (variableSuffix == null) {
            variableSuffix = "";
        }
        history = history == null ? List.of() : List.copyOf(history);
    }

    public List<OpenAiMessage> toMessages() {
        List<OpenAiMessage> messages = new ArrayList<>();
        messages.add(OpenAiMessage.system(stablePrefix));
        messages.add(OpenAiMessage.system(variableSuffix));
        messages.addAll(history);
        return List.copyOf(messages);
    }

    public List<OpenAiMessage> toMessagesWithConstraint(String extraConstraint) {
        if (extraConstraint == null || extraConstraint.isBlank()) {
            return toMessages();
        }
        List<OpenAiMessage> messages = new ArrayList<>(toMessages());
        messages.add(2, OpenAiMessage.system(extraConstraint));
        return List.copyOf(messages);
    }

    @Override
    public String toString() {
        return "AssembledPrompt[version=" + promptVersion
                + ", prefixChars=" + stablePrefix.length()
                + ", suffixChars=" + variableSuffix.length()
                + ", history=" + history.size()
                + "]";
    }
}

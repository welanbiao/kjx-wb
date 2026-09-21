package com.koujunxin.ai.prompt;

import java.util.ArrayList;
import java.util.List;

import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.session.ChatMessage;
import com.koujunxin.ai.session.ConversationSummary;

/**
 * 稳定前缀独立成第一条 system，可变后缀为第二条，以便 Prompt 缓存命中前缀。
 */
public final class PromptAssembler {

    public AssembledPrompt assemble(
            PromptRecord workPrompt,
            String runtimeStateJson,
            ConversationSummary summary,
            List<ChatMessage> recent,
            String jsonConstraint
    ) {
        if (workPrompt == null || workPrompt.kind() != PromptKind.WORK) {
            throw new IllegalArgumentException("runtime assembly requires WORK prompt");
        }
        String stable = workPrompt.stablePrefix();
        if (workPrompt.toolSpecJson() != null && !workPrompt.toolSpecJson().isBlank() && !"[]".equals(workPrompt.toolSpecJson())) {
            if (!stable.contains(workPrompt.toolSpecJson())) {
                stable = stable + "\n\n[tools]\n" + workPrompt.toolSpecJson();
            }
        }
        StringBuilder suffix = new StringBuilder();
        suffix.append("[state]\n").append(blankToEmptyObject(runtimeStateJson)).append('\n');
        suffix.append("[summary]\n").append(summary == null ? "" : nullToEmpty(summary.summaryText())).append('\n');
        if (jsonConstraint != null && !jsonConstraint.isBlank()) {
            suffix.append("[output]\n").append(jsonConstraint).append('\n');
        }
        List<OpenAiMessage> history = new ArrayList<>();
        if (recent != null) {
            for (ChatMessage message : recent) {
                if (message.isSystem()) {
                    continue;
                }
                history.add(message.toOpenAi());
            }
        }
        return new AssembledPrompt(workPrompt.version(), stable, suffix.toString(), List.copyOf(history));
    }

    private static String blankToEmptyObject(String json) {
        if (json == null || json.isBlank()) {
            return "{}";
        }
        return json;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

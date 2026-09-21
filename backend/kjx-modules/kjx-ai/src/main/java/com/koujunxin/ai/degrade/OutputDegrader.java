package com.koujunxin.ai.degrade;

import java.util.List;

import com.koujunxin.ai.api.TokenUsage;
import com.koujunxin.ai.api.port.CancellationToken;
import com.koujunxin.ai.api.port.UpstreamChatClient;
import com.koujunxin.ai.api.port.UpstreamChatRequest;
import com.koujunxin.ai.api.port.UpstreamChatResponse;
import com.koujunxin.ai.api.port.UpstreamException;
import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.openai.OpenAiTool;
import com.koujunxin.ai.prompt.AssembledPrompt;

/**
 * Tool Calling → JSON Schema → Prompt 约束解析；全部失败则返回可读文本。
 */
public final class OutputDegrader {

    public static final String JSON_CONSTRAINT = "只输出一个 JSON 对象，不要使用 Markdown 代码块，不要附加解释。";

    private final UpstreamChatClient upstream;
    private final JsonExtractor extractor;

    public OutputDegrader(UpstreamChatClient upstream) {
        this.upstream = upstream;
        this.extractor = new JsonExtractor();
    }

    public DegradedResult complete(
            AssembledPrompt assembled,
            List<OpenAiTool> tools,
            String jsonSchema,
            UpstreamChatRequest base,
            CancellationToken cancel
    ) {
        List<OpenAiTool> toolList = tools == null ? List.of() : tools;
        if (!toolList.isEmpty()) {
            try {
                UpstreamChatResponse response = upstream.complete(with(base, assembled.toMessages(), toolList, null), cancel);
                return DegradedResult.from(response, false);
            } catch (UpstreamException ex) {
                if (ex.kind() != UpstreamException.Kind.UNSUPPORTED_TOOLS) {
                    throw ex;
                }
            }
        }
        if (jsonSchema == null || jsonSchema.isBlank()) {
            UpstreamChatResponse response = upstream.complete(with(base, assembled.toMessages(), List.of(), null), cancel);
            return DegradedResult.from(response, false);
        }
        try {
            UpstreamChatResponse response = upstream.complete(with(base, assembled.toMessages(), List.of(), jsonSchema), cancel);
            String json = extractor.extractObject(response.content());
            if (json != null && extractor.matchesRequired(json, jsonSchema)) {
                return DegradedResult.from(rewriteContent(response, json), false);
            }
        } catch (UpstreamException ex) {
            if (ex.kind() != UpstreamException.Kind.UNSUPPORTED_SCHEMA) {
                throw ex;
            }
        }
        List<OpenAiMessage> constrained = assembled.toMessagesWithConstraint(JSON_CONSTRAINT);
        UpstreamChatResponse last = upstream.complete(with(base, constrained, List.of(), null), cancel);
        String json = extractor.extractObject(last.content());
        if (json != null && extractor.matchesRequired(json, jsonSchema)) {
            return DegradedResult.from(rewriteContent(last, json), false);
        }
        String readable = extractor.toReadable(last.content());
        return DegradedResult.from(rewriteContent(last, readable), true);
    }

    private static UpstreamChatRequest with(
            UpstreamChatRequest base,
            List<OpenAiMessage> messages,
            List<OpenAiTool> tools,
            String jsonSchema
    ) {
        return new UpstreamChatRequest(
                base.route(),
                messages,
                tools,
                jsonSchema,
                base.maxTokens(),
                false,
                base.secret());
    }

    private static UpstreamChatResponse rewriteContent(UpstreamChatResponse response, String content) {
        OpenAiMessage message = OpenAiMessage.assistant(content);
        TokenUsage usage = response.usage() == null ? TokenUsage.zero() : response.usage();
        return new UpstreamChatResponse(response.id(), response.model(), message, "stop", usage);
    }

    public record DegradedResult(UpstreamChatResponse response, boolean degradedToText) {
        static DegradedResult from(UpstreamChatResponse response, boolean degradedToText) {
            return new DegradedResult(response, degradedToText);
        }
    }
}
